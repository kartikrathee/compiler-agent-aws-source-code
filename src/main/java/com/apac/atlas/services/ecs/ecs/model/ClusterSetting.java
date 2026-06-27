package com.apac.atlas.services.ecs.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record ClusterSetting(String name, String value) {}
