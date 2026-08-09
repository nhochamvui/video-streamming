import * as cdk from 'aws-cdk-lib';
import { Stack } from 'aws-cdk-lib';
import { AmazonLinuxCpuType, CfnEIP, CfnEIPAssociation, Instance, InstanceType, MachineImage, Peer, Port, SecurityGroup, SubnetType, UserData, Vpc } from 'aws-cdk-lib/aws-ec2';
import { AsgCapacityProvider, AwsLogDriver, Cluster, ContainerImage, Ec2Service, Ec2TaskDefinition, EcsOptimizedImage, NetworkMode, PlacementConstraint, Secret } from 'aws-cdk-lib/aws-ecs';
import { ManagedPolicy, PolicyStatement, Role, ServicePrincipal } from 'aws-cdk-lib/aws-iam';
import { LogGroup, RetentionDays } from 'aws-cdk-lib/aws-logs';
import { StringParameter } from 'aws-cdk-lib/aws-ssm';
import { AutoScalingGroup } from 'aws-cdk-lib/aws-autoscaling';
import { Construct } from 'constructs';
import { InfraConfig } from './config';
import { createHlsStorage } from './storage-stack';

export interface CheapEcsEc2StackProps extends cdk.StackProps {
  readonly config: InfraConfig;
}

export class CheapEcsEc2Stack extends Stack {
  constructor(scope: Construct, id: string, props: CheapEcsEc2StackProps) {
    super(scope, id, props);

    const { config } = props;
    const storage = createHlsStorage(this);
    const ecsInstanceCount = Math.max(1, config.desiredAppCount);
    const appTaskCount = Math.max(1, config.desiredAppCount);

    const vpc = new Vpc(this, 'Vpc', {
      maxAzs: 1,
      natGateways: 0,
      subnetConfiguration: [{ name: 'public', subnetType: SubnetType.PUBLIC }]
    });

    const proxySecurityGroup = new SecurityGroup(this, 'ProxySecurityGroup', {
      vpc,
      allowAllOutbound: true,
      description: 'Cheap RTMP demo Traefik proxy access'
    });
    proxySecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(80), 'HTTP through Traefik');
    proxySecurityGroup.addIngressRule(Peer.anyIpv4(), Port.tcp(1935), 'RTMP through Traefik');
    proxySecurityGroup.addIngressRule(Peer.ipv4(config.adminCidr), Port.tcp(22), 'Optional SSH from admin CIDR');

    const securityGroup = new SecurityGroup(this, 'InstanceSecurityGroup', {
      vpc,
      allowAllOutbound: true,
      description: 'Cheap RTMP demo ECS instance access'
    });
    securityGroup.addIngressRule(proxySecurityGroup, Port.tcp(8888), 'HTTP from Traefik proxy');
    securityGroup.addIngressRule(proxySecurityGroup, Port.tcp(1935), 'RTMP from Traefik proxy');
    securityGroup.addIngressRule(Peer.ipv4(config.adminCidr), Port.tcp(22), 'Optional SSH from admin CIDR');
    proxySecurityGroup.addIngressRule(securityGroup, Port.tcp(6379), 'Redis from ECS app instances');

    const cluster = new Cluster(this, 'Cluster', { vpc });
    const userData = UserData.forLinux();
    userData.addCommands(
      'echo ECS_ENABLE_CONTAINER_METADATA=true >> /etc/ecs/ecs.config',
      'echo \'ECS_AVAILABLE_LOGGING_DRIVERS=["json-file","awslogs"]\' >> /etc/ecs/ecs.config'
    );

    const instanceRole = new Role(this, 'EcsInstanceRole', {
      assumedBy: new ServicePrincipal('ec2.amazonaws.com'),
      managedPolicies: [
        ManagedPolicy.fromAwsManagedPolicyName('service-role/AmazonEC2ContainerServiceforEC2Role')
      ]
    });

    const autoScalingGroup = new AutoScalingGroup(this, 'EcsCapacity', {
      vpc,
      instanceType: config.instanceType,
      machineImage: EcsOptimizedImage.amazonLinux2023(),
      role: instanceRole,
      securityGroup,
      userData,
      minCapacity: ecsInstanceCount,
      maxCapacity: Math.max(config.maxAppCount, ecsInstanceCount),
      desiredCapacity: ecsInstanceCount,
      vpcSubnets: { subnetType: SubnetType.PUBLIC }
    });

    const capacityProvider = new AsgCapacityProvider(this, 'CapacityProvider', {
      autoScalingGroup,
      enableManagedTerminationProtection: false
    });
    cluster.addAsgCapacityProvider(capacityProvider);

    const logGroup = new LogGroup(this, 'LogGroup', {
      retention: RetentionDays.THREE_DAYS,
      removalPolicy: cdk.RemovalPolicy.DESTROY
    });

    const executionRole = new Role(this, 'TaskExecutionRole', {
      assumedBy: new ServicePrincipal('ecs-tasks.amazonaws.com'),
      managedPolicies: [ManagedPolicy.fromAwsManagedPolicyName('service-role/AmazonECSTaskExecutionRolePolicy')]
    });

    const taskRole = new Role(this, 'TaskRole', {
      assumedBy: new ServicePrincipal('ecs-tasks.amazonaws.com')
    });
    storage.bucket.grantReadWrite(taskRole);
    executionRole.addToPolicy(new PolicyStatement({
      actions: ['ssm:GetParameter', 'ssm:GetParameters'],
      resources: [`arn:aws:ssm:${this.region}:${this.account}:parameter/rtmp/demo/hmac-secret`]
    }));

    const secretParameter = StringParameter.fromStringParameterName(this, 'HmacSecretParameter', '/rtmp/demo/hmac-secret');

    const proxyRole = new Role(this, 'ProxyInstanceRole', {
      assumedBy: new ServicePrincipal('ec2.amazonaws.com')
    });
    proxyRole.addToPolicy(new PolicyStatement({
      actions: [
        'autoscaling:DescribeAutoScalingGroups',
        'ec2:DescribeInstances'
      ],
      resources: ['*']
    }));

    const proxyUserData = UserData.forLinux();
    proxyUserData.addCommands(
      'dnf install -y docker awscli jq',
      'systemctl enable --now docker',
      'mkdir -p /etc/traefik/dynamic',
      'cat > /etc/traefik/traefik.yml <<\'EOF\'',
      'entryPoints:',
      '  web:',
      '    address: ":80"',
      '  rtmp:',
      '    address: ":1935"',
      'providers:',
      '  file:',
      '    directory: /etc/traefik/dynamic',
      '    watch: true',
      'log:',
      '  level: INFO',
      'EOF',
      'docker run -d --name redis --restart unless-stopped -p 6379:6379 redis:7-alpine',
      'docker run -d --name traefik --restart unless-stopped --network host -v /etc/traefik:/etc/traefik:ro traefik:v3.1',
      'cat > /usr/local/bin/render-traefik-backends <<\'EOF\'',
      '#!/bin/bash',
      'set -euo pipefail',
      `REGION="${this.region}"`,
      `ASG_NAME="${autoScalingGroup.autoScalingGroupName}"`,
      'TMP_FILE="$(mktemp)"',
      'FINAL_FILE="/etc/traefik/dynamic/ecs.yml"',
      'INSTANCE_IDS="$(aws autoscaling describe-auto-scaling-groups --region "$REGION" --auto-scaling-group-names "$ASG_NAME" --query \'AutoScalingGroups[0].Instances[?LifecycleState==`InService`].InstanceId\' --output text)"',
      'PRIVATE_IPS=""',
      'if [ -n "$INSTANCE_IDS" ]; then',
      '  PRIVATE_IPS="$(aws ec2 describe-instances --region "$REGION" --instance-ids $INSTANCE_IDS --query \'Reservations[].Instances[?State.Name==`running`].PrivateIpAddress\' --output text)"',
      'fi',
      'cat > "$TMP_FILE" <<\'YAML\'',
      'http:',
      '  routers:',
      '    app:',
      '      entryPoints:',
      '        - web',
      '      rule: PathPrefix(`/`)',
      '      service: app',
      '  services:',
      '    app:',
      '      loadBalancer:',
      '        servers:',
      'YAML',
      'if [ -z "$PRIVATE_IPS" ]; then',
      '  echo "          - url: http://127.0.0.1:65535" >> "$TMP_FILE"',
      'else',
      '  for ip in $PRIVATE_IPS; do echo "          - url: http://$ip:8888" >> "$TMP_FILE"; done',
      'fi',
      'cat >> "$TMP_FILE" <<\'YAML\'',
      'tcp:',
      '  routers:',
      '    rtmp:',
      '      entryPoints:',
      '        - rtmp',
      '      rule: HostSNI(`*`)',
      '      service: rtmp',
      '  services:',
      '    rtmp:',
      '      loadBalancer:',
      '        servers:',
      'YAML',
      'if [ -z "$PRIVATE_IPS" ]; then',
      '  echo "          - address: 127.0.0.1:65535" >> "$TMP_FILE"',
      'else',
      '  for ip in $PRIVATE_IPS; do echo "          - address: $ip:1935" >> "$TMP_FILE"; done',
      'fi',
      'if ! cmp -s "$TMP_FILE" "$FINAL_FILE"; then mv "$TMP_FILE" "$FINAL_FILE"; else rm "$TMP_FILE"; fi',
      'EOF',
      'chmod +x /usr/local/bin/render-traefik-backends',
      'cat > /etc/systemd/system/traefik-backends.service <<\'EOF\'',
      '[Unit]',
      'Description=Render Traefik ECS backend config',
      '',
      '[Service]',
      'Type=oneshot',
      'ExecStart=/usr/local/bin/render-traefik-backends',
      'EOF',
      'cat > /etc/systemd/system/traefik-backends.timer <<\'EOF\'',
      '[Unit]',
      'Description=Refresh Traefik ECS backend config',
      '',
      '[Timer]',
      'OnBootSec=20s',
      'OnUnitActiveSec=15s',
      'Unit=traefik-backends.service',
      '',
      '[Install]',
      'WantedBy=timers.target',
      'EOF',
      'systemctl daemon-reload',
      'systemctl enable --now traefik-backends.timer'
    );

    const proxyInstance = new Instance(this, 'ProxyInstance', {
      vpc,
      instanceType: new InstanceType('t4g.micro'),
      machineImage: MachineImage.latestAmazonLinux2023({ cpuType: AmazonLinuxCpuType.ARM_64 }),
      role: proxyRole,
      securityGroup: proxySecurityGroup,
      userData: proxyUserData,
      vpcSubnets: { subnetType: SubnetType.PUBLIC }
    });

    const proxyElasticIp = new CfnEIP(this, 'ProxyEip', { domain: 'vpc' });
    new CfnEIPAssociation(this, 'ProxyElasticIpAssociation', {
      allocationId: proxyElasticIp.attrAllocationId,
      instanceId: proxyInstance.instanceId
    });

    const taskDefinition = new Ec2TaskDefinition(this, 'AppTask', {
      networkMode: NetworkMode.HOST,
      executionRole,
      taskRole,
      volumes: [{ name: 'hls' }]
    });
    const appContainer = taskDefinition.addContainer('rtmp-app', {
      image: ContainerImage.fromRegistry(config.appImage),
      memoryReservationMiB: 384,
      secrets: {
        RTMP_HMAC_SECRET: Secret.fromSsmParameter(secretParameter)
      },
      environment: {
        MICRONAUT_SERVER_PORT: '8888',
        REDIS_URI: `redis://${proxyInstance.instancePrivateIp}:6379`,
        RTMP_SERVER_ID: 'auto',
        RTMP_PORT: '1935',
        RTMP_ENDPOINT: `rtmp://${config.rtmpHost}:1935/live`,
        RTMP_PLAYBACK_BASE_URL: `https://${storage.distribution.distributionDomainName}`,
        RTMP_HLS_ROOT: '/app/hls'
      },
      logging: new AwsLogDriver({ streamPrefix: 'app', logGroup })
    });
    appContainer.addPortMappings(
      { containerPort: 8888, hostPort: 8888 },
      { containerPort: 1935, hostPort: 1935 }
    );
    appContainer.addMountPoints({ containerPath: '/app/hls', sourceVolume: 'hls', readOnly: false });

    const uploader = taskDefinition.addContainer('hls-uploader', {
      image: ContainerImage.fromRegistry('public.ecr.aws/aws-cli/aws-cli:latest'),
      essential: false,
      memoryReservationMiB: 96,
      entryPoint: ['sh', '-c'],
      command: [`while true; do aws s3 sync /app/hls s3://${storage.bucket.bucketName}/hls/ --cache-control 'max-age=2'; sleep 2; done`],
      logging: new AwsLogDriver({ streamPrefix: 'uploader', logGroup })
    });
    uploader.addMountPoints({ containerPath: '/app/hls', sourceVolume: 'hls', readOnly: true });

    new Ec2Service(this, 'AppService', {
      cluster,
      taskDefinition,
      desiredCount: appTaskCount,
      circuitBreaker: { rollback: false },
      minHealthyPercent: 0,
      maxHealthyPercent: 100,
      placementConstraints: [PlacementConstraint.distinctInstances()],
      capacityProviderStrategies: [{ capacityProvider: capacityProvider.capacityProviderName, weight: 1 }]
    });

    new cdk.CfnOutput(this, 'HttpUrl', { value: `http://${config.rtmpHost}/` });
    new cdk.CfnOutput(this, 'ProxyElasticIp', { value: proxyElasticIp.ref });
    new cdk.CfnOutput(this, 'ProxyInstanceId', { value: proxyInstance.instanceId });
    new cdk.CfnOutput(this, 'AutoScalingGroupName', { value: autoScalingGroup.autoScalingGroupName });
    new cdk.CfnOutput(this, 'PlaybackBaseUrl', { value: `https://${storage.distribution.distributionDomainName}` });
    new cdk.CfnOutput(this, 'HlsBucketName', { value: storage.bucket.bucketName });
    new cdk.CfnOutput(this, 'RtmpUrl', { value: `rtmp://${config.rtmpHost}:1935/live` });
    new cdk.CfnOutput(this, 'DomainConfiguration', { value: `Point ${config.rtmpHost} to Elastic IP ${proxyElasticIp.ref}` });
  }
}
