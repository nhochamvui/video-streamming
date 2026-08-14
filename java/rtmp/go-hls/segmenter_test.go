package main

import (
	"encoding/binary"
	"encoding/hex"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// real config bytes extracted from a reference FLV produced by ffmpeg
const (
	testSPS = "6764001e919680a02ff89c0440000003004000000f23c58ba801"
	testPPS = "68ce3192"
	testASC = "120856e500"
)

var (
	testSPSBytes, _ = hex.DecodeString(testSPS)
	testPPSBytes, _ = hex.DecodeString(testPPS)
	testASCBytes, _ = hex.DecodeString(testASC)
)

func testAVCConfig() []byte {
	body := []byte{0x01, 0x42, 0x00, 0x1e, 0xff}
	body = append(body, 0xe1) // numSPS=1
	body = append(body, byte(len(testSPSBytes)>>8), byte(len(testSPSBytes)))
	body = append(body, testSPSBytes...)
	body = append(body, 0x01) // numPPS=1
	body = append(body, byte(len(testPPSBytes)>>8), byte(len(testPPSBytes)))
	body = append(body, testPPSBytes...)
	// FLV video tag: codecID=7, packetType=0 (config), cts=0
	return append([]byte{0x17, 0x00, 0, 0, 0}, body...)
}

func testAVCFrame(key bool, cts int32) []byte {
	nalu := []byte{0x41, 0x88, 0x80, 0x00} // type 1 (non-IDR)
	if key {
		nalu = []byte{0x65, 0x88, 0x80, 0x00} // type 5 (IDR)
	}
	body := make([]byte, 4+len(nalu))
	binary.BigEndian.PutUint32(body, uint32(len(nalu)))
	copy(body[4:], nalu)
	ctsBytes := []byte{byte(cts >> 16), byte(cts >> 8), byte(cts)}
	// FLV video tag: codecID=7, packetType=1 (NALU), cts 3 bytes
	return append([]byte{0x17, 0x01, ctsBytes[0], ctsBytes[1], ctsBytes[2]}, body...)
}

func testAACConfig() []byte {
	// FLV audio tag: format=10 (AAC), rate=3, size=2, type=2 => 0xAF, packetType=0
	return append([]byte{0xAF, 0x00}, testASCBytes...)
}

func testAACFrame() []byte {
	// FLV audio tag: format=10, packetType=1, raw AAC frame
	return append([]byte{0xAF, 0x01}, make([]byte, 128)...)
}

// feedVideo feeds N video frames at 30fps with a keyframe every gop frames,
// starting at startTS (ms).
func feedVideo(s *Segmenter, startTS, count, gop, fps int) int {
	ts := startTS
	for i := 0; i < count; i++ {
		key := (i % gop) == 0
		if err := s.Process(tagTypeVideo, ts, testAVCFrame(key, 0)); err != nil {
			return -1
		}
		ts += 1000 / fps
	}
	return ts
}

// feedAudio feeds AAC frames spaced at ~23ms covering the given ms range.
func feedAudio(s *Segmenter, startTS, endTS int) {
	for ts := startTS; ts < endTS; ts += 23 {
		if err := s.Process(tagTypeAudio, ts, testAACFrame()); err != nil {
			return
		}
	}
}

func newTestSegmenter(t *testing.T, targetMS int64) (*Segmenter, string) {
	t.Helper()
	dir := t.TempDir()
	s := NewSegmenter(dir, targetMS, 10, 1)
	if err := s.ensureInit(true); err != nil {
		t.Fatalf("ensureInit: %v", err)
	}
	// init.mp4 should NOT be written with no codecs known
	if _, err := os.Stat(filepath.Join(dir, "init.mp4")); !os.IsNotExist(err) {
		t.Fatalf("init.mp4 written before any config")
	}
	return s, dir
}

func TestBasicSegmentation(t *testing.T) {
	s, dir := newTestSegmenter(t, 1000)

	s.Process(tagTypeVideo, 0, testAVCConfig())
	s.Process(tagTypeAudio, 0, testAACConfig())

	// 5 seconds, 30fps, keyframe every 30 frames (1s)
	last := feedVideo(s, 0, 125, 25, 25)
	feedAudio(s, 0, last)

	if err := s.Finish(); err != nil {
		t.Fatalf("Finish: %v", err)
	}

	// 5 keyframes at 0/1000/2000/3000/4000 => 5 segments; stream ends at ts=4960
	expectedDur := []int64{1000, 1000, 1000, 1000, 960}
	if len(s.written) != len(expectedDur) {
		t.Fatalf("expected %d segments, got %d: %+v", len(expectedDur), len(s.written), s.written)
	}
	for i, seg := range s.written {
		if seg.num != i+1 {
			t.Errorf("segment %d num=%d", i, seg.num)
		}
		if seg.durMS != expectedDur[i] {
			t.Errorf("segment %d durMS=%d, want %d", i, seg.durMS, expectedDur[i])
		}
		if _, err := os.Stat(filepath.Join(dir, "output.m3u8")); err != nil {
			t.Fatalf("playlist missing: %v", err)
		}
		if _, err := os.Stat(filepath.Join(dir, "output_1.m4s")); err != nil {
			t.Fatalf("segment file missing: %v", err)
		}
	}

	// playlist contents
	pl, _ := os.ReadFile(filepath.Join(dir, "output.m3u8"))
	str := string(pl)
	if !strings.Contains(str, "#EXT-X-VERSION:7") {
		t.Errorf("missing version tag:\n%s", str)
	}
	if !strings.Contains(str, "#EXT-X-MAP:URI=\"init.mp4\"") {
		t.Errorf("missing map tag:\n%s", str)
	}
	if !strings.Contains(str, "#EXT-X-ENDLIST") {
		t.Errorf("missing endlist:\n%s", str)
	}
	for i := 0; i < 5; i++ {
		if !strings.Contains(str, "output_"+string(rune('1'+i))+".m4s") {
			t.Errorf("missing segment %d in playlist:\n%s", i+1, str)
		}
	}
	if strings.Contains(str, "#EXT-X-MEDIA-SEQUENCE:1") == false {
		t.Errorf("missing MEDIA-SEQUENCE:1:\n%s", str)
	}
}

func TestPlaylistWindowAndDeletion(t *testing.T) {
	s, dir := newTestSegmenter(t, 1000)

	s.Process(tagTypeVideo, 0, testAVCConfig())
	s.Process(tagTypeAudio, 0, testAACConfig())

	// 15 seconds => 15 segments > listSize 10
	last := feedVideo(s, 0, 375, 25, 25)
	feedAudio(s, 0, last)
	if err := s.Finish(); err != nil {
		t.Fatalf("Finish: %v", err)
	}

	pl, _ := os.ReadFile(filepath.Join(dir, "output.m3u8"))
	str := string(pl)
	if !strings.Contains(str, "#EXT-X-MEDIA-SEQUENCE:6") {
		t.Errorf("expected MEDIA-SEQUENCE:6 (window [6..15]), got:\n%s", str)
	}
	// window should have 10 entries
	entries := 0
	for _, line := range strings.Split(str, "\n") {
		if strings.HasPrefix(line, "output_") {
			entries++
		}
	}
	if entries != 10 {
		t.Errorf("expected 10 entries in window, got %d", entries)
	}
	// delete_threshold=1: keep num >= firstInWindow-1 = 5, so output_4 and below deleted
	if _, err := os.Stat(filepath.Join(dir, "output_5.m4s")); err != nil {
		t.Errorf("output_5 should be kept, err=%v", err)
	}
	if _, err := os.Stat(filepath.Join(dir, "output_4.m4s")); !os.IsNotExist(err) {
		t.Errorf("output_4 should be deleted")
	}
}

func TestInitRequiresBothCodecs(t *testing.T) {
	s, dir := newTestSegmenter(t, 1000)

	// only video config: init must NOT be written yet
	s.Process(tagTypeVideo, 0, testAVCConfig())
	if _, err := os.Stat(filepath.Join(dir, "init.mp4")); !os.IsNotExist(err) {
		t.Fatalf("init.mp4 written before audio config")
	}
	// audio config arrives later: now init is written
	s.Process(tagTypeAudio, 0, testAACConfig())
	if _, err := os.Stat(filepath.Join(dir, "init.mp4")); err != nil {
		t.Fatalf("init.mp4 not written after both configs: %v", err)
	}
}

func TestSkipsUntilFirstKeyframe(t *testing.T) {
	s, _ := newTestSegmenter(t, 1000)
	s.Process(tagTypeVideo, 0, testAVCConfig())

	// non-keyframe video before first keyframe must be ignored
	s.Process(tagTypeVideo, 0, testAVCFrame(false, 0))
	s.Process(tagTypeVideo, 33, testAVCFrame(false, 0))
	s.Process(tagTypeVideo, 100, testAVCFrame(true, 0)) // first keyframe
	s.Process(tagTypeVideo, 1100, testAVCFrame(true, 0)) // triggers rotation

	if len(s.written) != 1 {
		t.Fatalf("expected 1 segment, got %d", len(s.written))
	}
	if s.written[0].durMS != 1000 {
		t.Errorf("durMS=%d, want 1000", s.written[0].durMS)
	}
}

func TestVideoOnly(t *testing.T) {
	s, dir := newTestSegmenter(t, 1000)
	s.Process(tagTypeVideo, 0, testAVCConfig())
	feedVideo(s, 0, 100, 25, 25)
	if err := s.Finish(); err != nil {
		t.Fatalf("Finish: %v", err)
	}
	if len(s.written) != 4 {
		t.Fatalf("expected 4 segments, got %d", len(s.written))
	}
	if _, err := os.Stat(filepath.Join(dir, "init.mp4")); err != nil {
		t.Fatalf("init.mp4 missing: %v", err)
	}
}

func TestSplitByTime(t *testing.T) {
	s, _ := newTestSegmenter(t, 1000)
	s.Process(tagTypeVideo, 0, testAVCConfig())
	s.Process(tagTypeAudio, 0, testAACConfig())

	// 3 seconds at 30fps with a 2s GOP (keyframe every 60 frames).
	// Split-by-time should yield ~3 one-second segments, not 2 keyframe-aligned segments.
	last := feedVideo(s, 0, 90, 60, 30)
	feedAudio(s, 0, last)
	if err := s.Finish(); err != nil {
		t.Fatalf("Finish: %v", err)
	}

	if len(s.written) != 3 {
		t.Fatalf("expected 3 segments (split by time), got %d: %+v", len(s.written), s.written)
	}
	for i := 0; i < 2; i++ {
		if seg := s.written[i]; seg.durMS < 900 || seg.durMS > 1100 {
			t.Errorf("segment %d durMS=%d, want ~1000", i, seg.durMS)
		}
	}
	if s.written[2].durMS >= 1000 {
		t.Errorf("last segment durMS=%d, want <1000 (tail)", s.written[2].durMS)
	}
}
