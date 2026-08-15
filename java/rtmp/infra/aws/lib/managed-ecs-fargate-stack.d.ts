import * as cdk from 'aws-cdk-lib';
import { Stack } from 'aws-cdk-lib';
import { Construct } from 'constructs';
import { InfraConfig } from './config';
export interface ManagedEcsFargateStackProps extends cdk.StackProps {
    readonly config: InfraConfig;
}
export interface ManagedInfraRefs {
    readonly clusterName: string;
    readonly clusterArn: string;
    readonly taskRoleArn: string;
    readonly executionRoleArn: string;
    readonly logGroupName: string;
    readonly bucketName: string;
    readonly distributionDomainName: string;
    readonly redisUri: string;
    readonly hmacSecretName: string;
    readonly serviceSecurityGroupId: string;
    readonly albArn: string;
    readonly albSecurityGroupId: string;
    readonly nlbArn: string;
    readonly nlbDnsName: string;
    readonly vpcId: string;
    readonly publicSubnetIds: string;
    readonly availabilityZones: string;
}
export declare function exportManagedInfra(scope: Construct, config: InfraConfig, refs: ManagedInfraRefs): void;
export declare function importManagedInfra(scope: Construct, config: InfraConfig): ManagedInfraRefs;
export declare function createManagedInfra(scope: Construct, config: InfraConfig): ManagedInfraRefs;
export declare function createManagedApp(scope: Construct, config: InfraConfig, refs: ManagedInfraRefs): void;
export declare class ManagedEcsFargateInfraStack extends Stack {
    constructor(scope: Construct, id: string, props: ManagedEcsFargateStackProps);
}
export declare class ManagedEcsFargateAppStack extends Stack {
    constructor(scope: Construct, id: string, props: ManagedEcsFargateStackProps);
}
export declare class ManagedEcsFargateStack extends Stack {
    constructor(scope: Construct, id: string, props: ManagedEcsFargateStackProps);
}
