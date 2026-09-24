package com.pancake.surviving_the_aftermath.common.module.weighted;

import com.pancake.surviving_the_aftermath.api.module.IWeightedModule;
import net.minecraft.util.random.WeightedList;
import net.minecraft.util.random.Weighted;


import java.util.List;


public abstract class BaseWeightedModule<T> implements IWeightedModule<T> {
    protected List<Weighted<T>> list;

    public BaseWeightedModule(List<Weighted<T>> list) {
        this.list = list;
    }

    public BaseWeightedModule() {
    }

    @Override
    public void add(T t, int weight) {
        this.list.add(new Weighted<>(t, weight));
    }

    @Override
    public void remove(T t) {
        this.list.removeIf(wrapper -> wrapper.value().equals(t));
    }

    @Override
    public WeightedList<T> getWeightedList() {
        WeightedList.Builder<T> builder = WeightedList.builder();
        this.list.forEach(build -> builder.add(build.value(), build.weight()));
        return builder.build();
    }

    @Override
    public List<Weighted<T>> getList() {
        return this.list;
    }
}