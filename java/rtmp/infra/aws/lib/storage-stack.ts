import { Duration, RemovalPolicy } from 'aws-cdk-lib';
import { CachePolicy, Distribution, OriginAccessIdentity, OriginRequestPolicy, ResponseHeadersPolicy, ViewerProtocolPolicy } from 'aws-cdk-lib/aws-cloudfront';
import { S3Origin } from 'aws-cdk-lib/aws-cloudfront-origins';
import { Bucket, BlockPublicAccess, BucketEncryption, HttpMethods } from 'aws-cdk-lib/aws-s3';
import { Construct } from 'constructs';

export interface HlsStorage {
  readonly bucket: Bucket;
  readonly distribution: Distribution;
}

export function createHlsStorage(scope: Construct): HlsStorage {
  const bucket = new Bucket(scope, 'HlsBucket', {
    blockPublicAccess: BlockPublicAccess.BLOCK_ALL,
    encryption: BucketEncryption.S3_MANAGED,
    lifecycleRules: [{
      expiration: Duration.days(1),
      prefix: 'hls/'
    }],
    autoDeleteObjects: true,
    removalPolicy: RemovalPolicy.DESTROY,
    cors: [{
      allowedOrigins: ['*'],
      allowedMethods: [HttpMethods.GET, HttpMethods.HEAD],
      allowedHeaders: ['*'],
      maxAge: 3000
    }]
  });

  const originAccessIdentity = new OriginAccessIdentity(scope, 'HlsOriginAccessIdentity');
  bucket.grantRead(originAccessIdentity);

  const mediaCachePolicy = new CachePolicy(scope, 'HlsMediaCachePolicy', {
    comment: 'Cache HLS segments at the edge',
    defaultTtl: Duration.seconds(30),
    minTtl: Duration.seconds(0),
    maxTtl: Duration.seconds(120),
    enableAcceptEncodingBrotli: true,
    enableAcceptEncodingGzip: true
  });

  const playlistCachePolicy = new CachePolicy(scope, 'HlsPlaylistCachePolicy', {
    comment: 'Do not cache HLS playlists',
    defaultTtl: Duration.seconds(0),
    minTtl: Duration.seconds(0),
    maxTtl: Duration.seconds(0)
  });

  const playlistResponseHeaders = new ResponseHeadersPolicy(scope, 'HlsPlaylistResponseHeaders', {
    comment: 'No-store for HLS playlists',
    customHeadersBehavior: {
      customHeaders: [{ header: 'Cache-Control', value: 'no-store', override: true }]
    }
  });

  const s3Origin = new S3Origin(bucket, { originAccessIdentity });

  const distribution = new Distribution(scope, 'HlsDistribution', {
    defaultBehavior: {
      origin: s3Origin,
      cachePolicy: mediaCachePolicy,
      originRequestPolicy: OriginRequestPolicy.CORS_S3_ORIGIN,
      viewerProtocolPolicy: ViewerProtocolPolicy.REDIRECT_TO_HTTPS
    },
    additionalBehaviors: {
      '*.m3u8': {
        origin: s3Origin,
        cachePolicy: playlistCachePolicy,
        originRequestPolicy: OriginRequestPolicy.CORS_S3_ORIGIN,
        responseHeadersPolicy: playlistResponseHeaders,
        viewerProtocolPolicy: ViewerProtocolPolicy.REDIRECT_TO_HTTPS
      }
    },
    comment: 'RTMP demo HLS playback cache'
  });

  return { bucket, distribution };
}

