import { Duration, RemovalPolicy } from 'aws-cdk-lib';
import { CachePolicy, Distribution, OriginAccessIdentity, ViewerProtocolPolicy } from 'aws-cdk-lib/aws-cloudfront';
import { S3Origin } from 'aws-cdk-lib/aws-cloudfront-origins';
import { Bucket, BlockPublicAccess, BucketEncryption } from 'aws-cdk-lib/aws-s3';
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
    removalPolicy: RemovalPolicy.DESTROY
  });

  const originAccessIdentity = new OriginAccessIdentity(scope, 'HlsOriginAccessIdentity');
  bucket.grantRead(originAccessIdentity);

  const cachePolicy = new CachePolicy(scope, 'HlsCachePolicy', {
    comment: 'Respect live HLS origin cache headers',
    defaultTtl: Duration.seconds(1),
    minTtl: Duration.seconds(0),
    maxTtl: Duration.seconds(30),
    enableAcceptEncodingBrotli: true,
    enableAcceptEncodingGzip: true
  });

  const distribution = new Distribution(scope, 'HlsDistribution', {
    defaultBehavior: {
      origin: new S3Origin(bucket, { originAccessIdentity }),
      cachePolicy,
      viewerProtocolPolicy: ViewerProtocolPolicy.REDIRECT_TO_HTTPS
    },
    comment: 'RTMP demo HLS playback cache'
  });

  return { bucket, distribution };
}
