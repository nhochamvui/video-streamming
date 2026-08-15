"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.createHlsStorage = createHlsStorage;
const aws_cdk_lib_1 = require("aws-cdk-lib");
const aws_cloudfront_1 = require("aws-cdk-lib/aws-cloudfront");
const aws_cloudfront_origins_1 = require("aws-cdk-lib/aws-cloudfront-origins");
const aws_s3_1 = require("aws-cdk-lib/aws-s3");
function createHlsStorage(scope) {
    const bucket = new aws_s3_1.Bucket(scope, 'HlsBucket', {
        blockPublicAccess: aws_s3_1.BlockPublicAccess.BLOCK_ALL,
        encryption: aws_s3_1.BucketEncryption.S3_MANAGED,
        lifecycleRules: [{
                expiration: aws_cdk_lib_1.Duration.days(1),
                prefix: 'hls/'
            }],
        autoDeleteObjects: true,
        removalPolicy: aws_cdk_lib_1.RemovalPolicy.DESTROY,
        cors: [{
                allowedOrigins: ['*'],
                allowedMethods: [aws_s3_1.HttpMethods.GET, aws_s3_1.HttpMethods.HEAD],
                allowedHeaders: ['*'],
                maxAge: 3000
            }]
    });
    const originAccessIdentity = new aws_cloudfront_1.OriginAccessIdentity(scope, 'HlsOriginAccessIdentity');
    bucket.grantRead(originAccessIdentity);
    const mediaCachePolicy = new aws_cloudfront_1.CachePolicy(scope, 'HlsMediaCachePolicy', {
        comment: 'Cache HLS segments at the edge',
        defaultTtl: aws_cdk_lib_1.Duration.seconds(30),
        minTtl: aws_cdk_lib_1.Duration.seconds(0),
        maxTtl: aws_cdk_lib_1.Duration.seconds(120),
        enableAcceptEncodingBrotli: true,
        enableAcceptEncodingGzip: true
    });
    const playlistCachePolicy = new aws_cloudfront_1.CachePolicy(scope, 'HlsPlaylistCachePolicy', {
        comment: 'Do not cache HLS playlists',
        defaultTtl: aws_cdk_lib_1.Duration.seconds(0),
        minTtl: aws_cdk_lib_1.Duration.seconds(0),
        maxTtl: aws_cdk_lib_1.Duration.seconds(0)
    });
    const playlistResponseHeaders = new aws_cloudfront_1.ResponseHeadersPolicy(scope, 'HlsPlaylistResponseHeaders', {
        comment: 'No-store for HLS playlists',
        customHeadersBehavior: {
            customHeaders: [{ header: 'Cache-Control', value: 'no-store', override: true }]
        }
    });
    const s3Origin = new aws_cloudfront_origins_1.S3Origin(bucket, { originAccessIdentity });
    const distribution = new aws_cloudfront_1.Distribution(scope, 'HlsDistribution', {
        defaultBehavior: {
            origin: s3Origin,
            cachePolicy: mediaCachePolicy,
            originRequestPolicy: aws_cloudfront_1.OriginRequestPolicy.CORS_S3_ORIGIN,
            viewerProtocolPolicy: aws_cloudfront_1.ViewerProtocolPolicy.REDIRECT_TO_HTTPS
        },
        additionalBehaviors: {
            '*.m3u8': {
                origin: s3Origin,
                cachePolicy: playlistCachePolicy,
                originRequestPolicy: aws_cloudfront_1.OriginRequestPolicy.CORS_S3_ORIGIN,
                responseHeadersPolicy: playlistResponseHeaders,
                viewerProtocolPolicy: aws_cloudfront_1.ViewerProtocolPolicy.REDIRECT_TO_HTTPS
            }
        },
        comment: 'RTMP demo HLS playback cache'
    });
    return { bucket, distribution };
}
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoic3RvcmFnZS1zdGFjay5qcyIsInNvdXJjZVJvb3QiOiIiLCJzb3VyY2VzIjpbInN0b3JhZ2Utc3RhY2sudHMiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6Ijs7QUFXQSw0Q0FrRUM7QUE3RUQsNkNBQXNEO0FBQ3RELCtEQUErSjtBQUMvSiwrRUFBOEQ7QUFDOUQsK0NBQThGO0FBUTlGLFNBQWdCLGdCQUFnQixDQUFDLEtBQWdCO0lBQy9DLE1BQU0sTUFBTSxHQUFHLElBQUksZUFBTSxDQUFDLEtBQUssRUFBRSxXQUFXLEVBQUU7UUFDNUMsaUJBQWlCLEVBQUUsMEJBQWlCLENBQUMsU0FBUztRQUM5QyxVQUFVLEVBQUUseUJBQWdCLENBQUMsVUFBVTtRQUN2QyxjQUFjLEVBQUUsQ0FBQztnQkFDZixVQUFVLEVBQUUsc0JBQVEsQ0FBQyxJQUFJLENBQUMsQ0FBQyxDQUFDO2dCQUM1QixNQUFNLEVBQUUsTUFBTTthQUNmLENBQUM7UUFDRixpQkFBaUIsRUFBRSxJQUFJO1FBQ3ZCLGFBQWEsRUFBRSwyQkFBYSxDQUFDLE9BQU87UUFDcEMsSUFBSSxFQUFFLENBQUM7Z0JBQ0wsY0FBYyxFQUFFLENBQUMsR0FBRyxDQUFDO2dCQUNyQixjQUFjLEVBQUUsQ0FBQyxvQkFBVyxDQUFDLEdBQUcsRUFBRSxvQkFBVyxDQUFDLElBQUksQ0FBQztnQkFDbkQsY0FBYyxFQUFFLENBQUMsR0FBRyxDQUFDO2dCQUNyQixNQUFNLEVBQUUsSUFBSTthQUNiLENBQUM7S0FDSCxDQUFDLENBQUM7SUFFSCxNQUFNLG9CQUFvQixHQUFHLElBQUkscUNBQW9CLENBQUMsS0FBSyxFQUFFLHlCQUF5QixDQUFDLENBQUM7SUFDeEYsTUFBTSxDQUFDLFNBQVMsQ0FBQyxvQkFBb0IsQ0FBQyxDQUFDO0lBRXZDLE1BQU0sZ0JBQWdCLEdBQUcsSUFBSSw0QkFBVyxDQUFDLEtBQUssRUFBRSxxQkFBcUIsRUFBRTtRQUNyRSxPQUFPLEVBQUUsZ0NBQWdDO1FBQ3pDLFVBQVUsRUFBRSxzQkFBUSxDQUFDLE9BQU8sQ0FBQyxFQUFFLENBQUM7UUFDaEMsTUFBTSxFQUFFLHNCQUFRLENBQUMsT0FBTyxDQUFDLENBQUMsQ0FBQztRQUMzQixNQUFNLEVBQUUsc0JBQVEsQ0FBQyxPQUFPLENBQUMsR0FBRyxDQUFDO1FBQzdCLDBCQUEwQixFQUFFLElBQUk7UUFDaEMsd0JBQXdCLEVBQUUsSUFBSTtLQUMvQixDQUFDLENBQUM7SUFFSCxNQUFNLG1CQUFtQixHQUFHLElBQUksNEJBQVcsQ0FBQyxLQUFLLEVBQUUsd0JBQXdCLEVBQUU7UUFDM0UsT0FBTyxFQUFFLDRCQUE0QjtRQUNyQyxVQUFVLEVBQUUsc0JBQVEsQ0FBQyxPQUFPLENBQUMsQ0FBQyxDQUFDO1FBQy9CLE1BQU0sRUFBRSxzQkFBUSxDQUFDLE9BQU8sQ0FBQyxDQUFDLENBQUM7UUFDM0IsTUFBTSxFQUFFLHNCQUFRLENBQUMsT0FBTyxDQUFDLENBQUMsQ0FBQztLQUM1QixDQUFDLENBQUM7SUFFSCxNQUFNLHVCQUF1QixHQUFHLElBQUksc0NBQXFCLENBQUMsS0FBSyxFQUFFLDRCQUE0QixFQUFFO1FBQzdGLE9BQU8sRUFBRSw0QkFBNEI7UUFDckMscUJBQXFCLEVBQUU7WUFDckIsYUFBYSxFQUFFLENBQUMsRUFBRSxNQUFNLEVBQUUsZUFBZSxFQUFFLEtBQUssRUFBRSxVQUFVLEVBQUUsUUFBUSxFQUFFLElBQUksRUFBRSxDQUFDO1NBQ2hGO0tBQ0YsQ0FBQyxDQUFDO0lBRUgsTUFBTSxRQUFRLEdBQUcsSUFBSSxpQ0FBUSxDQUFDLE1BQU0sRUFBRSxFQUFFLG9CQUFvQixFQUFFLENBQUMsQ0FBQztJQUVoRSxNQUFNLFlBQVksR0FBRyxJQUFJLDZCQUFZLENBQUMsS0FBSyxFQUFFLGlCQUFpQixFQUFFO1FBQzlELGVBQWUsRUFBRTtZQUNmLE1BQU0sRUFBRSxRQUFRO1lBQ2hCLFdBQVcsRUFBRSxnQkFBZ0I7WUFDN0IsbUJBQW1CLEVBQUUsb0NBQW1CLENBQUMsY0FBYztZQUN2RCxvQkFBb0IsRUFBRSxxQ0FBb0IsQ0FBQyxpQkFBaUI7U0FDN0Q7UUFDRCxtQkFBbUIsRUFBRTtZQUNuQixRQUFRLEVBQUU7Z0JBQ1IsTUFBTSxFQUFFLFFBQVE7Z0JBQ2hCLFdBQVcsRUFBRSxtQkFBbUI7Z0JBQ2hDLG1CQUFtQixFQUFFLG9DQUFtQixDQUFDLGNBQWM7Z0JBQ3ZELHFCQUFxQixFQUFFLHVCQUF1QjtnQkFDOUMsb0JBQW9CLEVBQUUscUNBQW9CLENBQUMsaUJBQWlCO2FBQzdEO1NBQ0Y7UUFDRCxPQUFPLEVBQUUsOEJBQThCO0tBQ3hDLENBQUMsQ0FBQztJQUVILE9BQU8sRUFBRSxNQUFNLEVBQUUsWUFBWSxFQUFFLENBQUM7QUFDbEMsQ0FBQyIsInNvdXJjZXNDb250ZW50IjpbImltcG9ydCB7IER1cmF0aW9uLCBSZW1vdmFsUG9saWN5IH0gZnJvbSAnYXdzLWNkay1saWInO1xyXG5pbXBvcnQgeyBDYWNoZVBvbGljeSwgRGlzdHJpYnV0aW9uLCBPcmlnaW5BY2Nlc3NJZGVudGl0eSwgT3JpZ2luUmVxdWVzdFBvbGljeSwgUmVzcG9uc2VIZWFkZXJzUG9saWN5LCBWaWV3ZXJQcm90b2NvbFBvbGljeSB9IGZyb20gJ2F3cy1jZGstbGliL2F3cy1jbG91ZGZyb250JztcclxuaW1wb3J0IHsgUzNPcmlnaW4gfSBmcm9tICdhd3MtY2RrLWxpYi9hd3MtY2xvdWRmcm9udC1vcmlnaW5zJztcclxuaW1wb3J0IHsgQnVja2V0LCBCbG9ja1B1YmxpY0FjY2VzcywgQnVja2V0RW5jcnlwdGlvbiwgSHR0cE1ldGhvZHMgfSBmcm9tICdhd3MtY2RrLWxpYi9hd3MtczMnO1xyXG5pbXBvcnQgeyBDb25zdHJ1Y3QgfSBmcm9tICdjb25zdHJ1Y3RzJztcclxuXHJcbmV4cG9ydCBpbnRlcmZhY2UgSGxzU3RvcmFnZSB7XHJcbiAgcmVhZG9ubHkgYnVja2V0OiBCdWNrZXQ7XHJcbiAgcmVhZG9ubHkgZGlzdHJpYnV0aW9uOiBEaXN0cmlidXRpb247XHJcbn1cclxuXHJcbmV4cG9ydCBmdW5jdGlvbiBjcmVhdGVIbHNTdG9yYWdlKHNjb3BlOiBDb25zdHJ1Y3QpOiBIbHNTdG9yYWdlIHtcclxuICBjb25zdCBidWNrZXQgPSBuZXcgQnVja2V0KHNjb3BlLCAnSGxzQnVja2V0Jywge1xyXG4gICAgYmxvY2tQdWJsaWNBY2Nlc3M6IEJsb2NrUHVibGljQWNjZXNzLkJMT0NLX0FMTCxcclxuICAgIGVuY3J5cHRpb246IEJ1Y2tldEVuY3J5cHRpb24uUzNfTUFOQUdFRCxcclxuICAgIGxpZmVjeWNsZVJ1bGVzOiBbe1xyXG4gICAgICBleHBpcmF0aW9uOiBEdXJhdGlvbi5kYXlzKDEpLFxyXG4gICAgICBwcmVmaXg6ICdobHMvJ1xyXG4gICAgfV0sXHJcbiAgICBhdXRvRGVsZXRlT2JqZWN0czogdHJ1ZSxcclxuICAgIHJlbW92YWxQb2xpY3k6IFJlbW92YWxQb2xpY3kuREVTVFJPWSxcclxuICAgIGNvcnM6IFt7XHJcbiAgICAgIGFsbG93ZWRPcmlnaW5zOiBbJyonXSxcclxuICAgICAgYWxsb3dlZE1ldGhvZHM6IFtIdHRwTWV0aG9kcy5HRVQsIEh0dHBNZXRob2RzLkhFQURdLFxyXG4gICAgICBhbGxvd2VkSGVhZGVyczogWycqJ10sXHJcbiAgICAgIG1heEFnZTogMzAwMFxyXG4gICAgfV1cclxuICB9KTtcclxuXHJcbiAgY29uc3Qgb3JpZ2luQWNjZXNzSWRlbnRpdHkgPSBuZXcgT3JpZ2luQWNjZXNzSWRlbnRpdHkoc2NvcGUsICdIbHNPcmlnaW5BY2Nlc3NJZGVudGl0eScpO1xyXG4gIGJ1Y2tldC5ncmFudFJlYWQob3JpZ2luQWNjZXNzSWRlbnRpdHkpO1xyXG5cclxuICBjb25zdCBtZWRpYUNhY2hlUG9saWN5ID0gbmV3IENhY2hlUG9saWN5KHNjb3BlLCAnSGxzTWVkaWFDYWNoZVBvbGljeScsIHtcclxuICAgIGNvbW1lbnQ6ICdDYWNoZSBITFMgc2VnbWVudHMgYXQgdGhlIGVkZ2UnLFxyXG4gICAgZGVmYXVsdFR0bDogRHVyYXRpb24uc2Vjb25kcygzMCksXHJcbiAgICBtaW5UdGw6IER1cmF0aW9uLnNlY29uZHMoMCksXHJcbiAgICBtYXhUdGw6IER1cmF0aW9uLnNlY29uZHMoMTIwKSxcclxuICAgIGVuYWJsZUFjY2VwdEVuY29kaW5nQnJvdGxpOiB0cnVlLFxyXG4gICAgZW5hYmxlQWNjZXB0RW5jb2RpbmdHemlwOiB0cnVlXHJcbiAgfSk7XHJcblxyXG4gIGNvbnN0IHBsYXlsaXN0Q2FjaGVQb2xpY3kgPSBuZXcgQ2FjaGVQb2xpY3koc2NvcGUsICdIbHNQbGF5bGlzdENhY2hlUG9saWN5Jywge1xyXG4gICAgY29tbWVudDogJ0RvIG5vdCBjYWNoZSBITFMgcGxheWxpc3RzJyxcclxuICAgIGRlZmF1bHRUdGw6IER1cmF0aW9uLnNlY29uZHMoMCksXHJcbiAgICBtaW5UdGw6IER1cmF0aW9uLnNlY29uZHMoMCksXHJcbiAgICBtYXhUdGw6IER1cmF0aW9uLnNlY29uZHMoMClcclxuICB9KTtcclxuXHJcbiAgY29uc3QgcGxheWxpc3RSZXNwb25zZUhlYWRlcnMgPSBuZXcgUmVzcG9uc2VIZWFkZXJzUG9saWN5KHNjb3BlLCAnSGxzUGxheWxpc3RSZXNwb25zZUhlYWRlcnMnLCB7XHJcbiAgICBjb21tZW50OiAnTm8tc3RvcmUgZm9yIEhMUyBwbGF5bGlzdHMnLFxyXG4gICAgY3VzdG9tSGVhZGVyc0JlaGF2aW9yOiB7XHJcbiAgICAgIGN1c3RvbUhlYWRlcnM6IFt7IGhlYWRlcjogJ0NhY2hlLUNvbnRyb2wnLCB2YWx1ZTogJ25vLXN0b3JlJywgb3ZlcnJpZGU6IHRydWUgfV1cclxuICAgIH1cclxuICB9KTtcclxuXHJcbiAgY29uc3QgczNPcmlnaW4gPSBuZXcgUzNPcmlnaW4oYnVja2V0LCB7IG9yaWdpbkFjY2Vzc0lkZW50aXR5IH0pO1xyXG5cclxuICBjb25zdCBkaXN0cmlidXRpb24gPSBuZXcgRGlzdHJpYnV0aW9uKHNjb3BlLCAnSGxzRGlzdHJpYnV0aW9uJywge1xyXG4gICAgZGVmYXVsdEJlaGF2aW9yOiB7XHJcbiAgICAgIG9yaWdpbjogczNPcmlnaW4sXHJcbiAgICAgIGNhY2hlUG9saWN5OiBtZWRpYUNhY2hlUG9saWN5LFxyXG4gICAgICBvcmlnaW5SZXF1ZXN0UG9saWN5OiBPcmlnaW5SZXF1ZXN0UG9saWN5LkNPUlNfUzNfT1JJR0lOLFxyXG4gICAgICB2aWV3ZXJQcm90b2NvbFBvbGljeTogVmlld2VyUHJvdG9jb2xQb2xpY3kuUkVESVJFQ1RfVE9fSFRUUFNcclxuICAgIH0sXHJcbiAgICBhZGRpdGlvbmFsQmVoYXZpb3JzOiB7XHJcbiAgICAgICcqLm0zdTgnOiB7XHJcbiAgICAgICAgb3JpZ2luOiBzM09yaWdpbixcclxuICAgICAgICBjYWNoZVBvbGljeTogcGxheWxpc3RDYWNoZVBvbGljeSxcclxuICAgICAgICBvcmlnaW5SZXF1ZXN0UG9saWN5OiBPcmlnaW5SZXF1ZXN0UG9saWN5LkNPUlNfUzNfT1JJR0lOLFxyXG4gICAgICAgIHJlc3BvbnNlSGVhZGVyc1BvbGljeTogcGxheWxpc3RSZXNwb25zZUhlYWRlcnMsXHJcbiAgICAgICAgdmlld2VyUHJvdG9jb2xQb2xpY3k6IFZpZXdlclByb3RvY29sUG9saWN5LlJFRElSRUNUX1RPX0hUVFBTXHJcbiAgICAgIH1cclxuICAgIH0sXHJcbiAgICBjb21tZW50OiAnUlRNUCBkZW1vIEhMUyBwbGF5YmFjayBjYWNoZSdcclxuICB9KTtcclxuXHJcbiAgcmV0dXJuIHsgYnVja2V0LCBkaXN0cmlidXRpb24gfTtcclxufVxyXG5cclxuIl19