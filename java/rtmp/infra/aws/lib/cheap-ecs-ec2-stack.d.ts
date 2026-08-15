import * as cdk from 'aws-cdk-lib';
import { Stack } from 'aws-cdk-lib';
import { Construct } from 'constructs';
import { InfraConfig } from './config';
export interface CheapEcsEc2StackProps extends cdk.StackProps {
    readonly config: InfraConfig;
}
export interface CheapInfraRefs {
    readonly clusterName: string;
    readonly clusterArn: string;
    readonly capacityProviderName: string;
    readonly taskRoleArn: string;
    readonly executionRoleArn: string;
    readonly logGroupName: string;
    readonly bucketName: string;
    readonly distributionDomainName: string;
    readonly redisUri: string;
    readonly hmacSecretName: string;
    readonly vpcId: string;
    readonly publicSubnetIds: string;
    readonly availabilityZones: string;
}
export declare function exportCheapInfra(scope: Construct, refs: CheapInfraRefs): void;
export declare function importCheapInfra(scope: Construct): CheapInfraRefs;
export declare function createCheapInfra(scope: Construct, config: InfraConfig): CheapInfraRefs;
export declare function createCheapApp(scope: Construct, config: InfraConfig, refs: CheapInfraRefs): void;
export declare class CheapEcsEc2InfraStack extends Stack {
    constructor(scope: Construct, id: string, props: CheapEcsEc2StackProps);
}
export declare class CheapEcsEc2AppStack extends Stack {
    constructor(scope: Construct, id: string, props: CheapEcsEc2StackProps);
}
export declare class CheapEcsEc2Stack extends Stack {
    constructor(scope: Construct, id: string, props: CheapEcsEc2StackProps);
}
