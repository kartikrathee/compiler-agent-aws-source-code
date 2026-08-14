package com.apac.atlas.services.s3.model;

public record ObjectLockRetention(String mode, String unit, int value) {}
