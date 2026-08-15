import { Distribution } from 'aws-cdk-lib/aws-cloudfront';
import { Bucket } from 'aws-cdk-lib/aws-s3';
import { Construct } from 'constructs';
export interface HlsStorage {
    readonly bucket: Bucket;
    readonly distribution: Distribution;
}
export declare function createHlsStorage(scope: Construct): HlsStorage;
