import * as cdk from 'aws-cdk-lib';
import { InstanceType } from 'aws-cdk-lib/aws-ec2';
export type DeployMode = 'cheap' | 'managed';
export interface InfraConfig {
    readonly deployMode: DeployMode;
    readonly appImage: string;
    readonly instanceTypeName: string;
    readonly instanceType: InstanceType;
    readonly desiredAppCount: number;
    readonly maxAppCount: number;
    readonly enableAlb: boolean;
    readonly enableNlb: boolean;
    readonly enableElastiCache: boolean;
    readonly adminCidr: string;
    readonly rtmpHost: string;
}
export declare function getConfig(app: cdk.App): InfraConfig;
