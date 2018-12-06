package com.company.client;

import com.company.model.Model;

import org.immutables.value.Value;

@Value.Immutable @Model.WithBuilderOnly
public abstract class Client {

    public abstract String getKey();

}
