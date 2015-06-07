package org.fushihara.util;

public final class Ref<T> {
    private T value;
    public Ref(T val) {
	value=val;
    }
    public void set(T val){
	value=val;
    }
    public T get(){
	return value;
    }
}
