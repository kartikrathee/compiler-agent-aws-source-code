#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib';
import { FlociTestStack } from '../lib/atlas-stack';

const app = new cdk.App();
new FlociTestStack(app, 'FlociTestStack', {
  env: {
    account: '000000000000',
    region: 'us-east-1',
  },
});
