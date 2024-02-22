package com.pp.netty.test;

import com.pp.netty.util.Recycler;
import com.pp.netty.util.concurrent.FastThreadLocalThread;

public class RecyclerTest {

    private static final Recycler<Wife> WIFE = new Recycler<Wife>() {
        @Override
        protected Wife newObject(Handle<Wife> handle) {
            return new Wife(handle);
        }
    };

    public static void main(String[] args) throws Exception {

        //这里创建这个线程，是因为对象池要配合这个线程，和FastThreadLocal来使用
        new FastThreadLocalThread(() -> {
            Wife  wife = WIFE.get();
            wife.recycle();
            Wife  newWife = WIFE.get();
            System.out.println(wife == newWife);
        }, "FastThreadLocalThread-1").start();
    }

    static class Wife {

        private final Recycler.Handle<Wife> recyclerHandle;

        Wife(Recycler.Handle<Wife> recyclerHandle) {
            this.recyclerHandle = recyclerHandle;
        }

        void recycle() {
            recyclerHandle.recycle(this);
        }
    }
}
