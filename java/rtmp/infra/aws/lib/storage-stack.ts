import { RemovalPolicy } from 'aws-cdk-lib';
import { Distribution, OriginAccessIdentity, ViewerProtocolPolicy } from 'aws-cdk-lib/aws-cloudfront';
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
    autoDeleteObjects: true,
    removalPolicy: RemovalPolicy.DESTROY
  });

  const originAccessIdentity = new OriginAccessIdentity(scope, 'HlsOriginAccessIdentity');
  bucket.grantRead(originAccessIdentity);

  const distribution = new Distribution(scope, 'HlsDistribution', {
    defaultBehavior: {
      origin: new S3Origin(bucket, { originAccessIdentity }),
      viewerProtocolPolicy: ViewerProtocolPolicy.REDIRECT_TO_HTTPS
    },
    comment: 'RTMP demo HLS playback cache'
  });

  return { bucket, distribution };
}
