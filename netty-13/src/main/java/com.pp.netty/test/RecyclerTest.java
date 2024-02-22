package com.pp.netty.test;

import com.pp.netty.util.Recycler;
import com.pp.netty.util.concurrent.FastThreadLocalThread;


public class RecyclerTest {

    public static void main(String[] args) throws Exception {

        //主线程从对象池中获得了对象
        Wife  wife = Wife.newInstance();

        //但是这个对象马上就交给一个新的线程使用了，并且在新的线程中被归还给对象池
        new Thread(() -> {
            //新的线程把对象归还给对象池，这里请大家思考一下，将对象归还给对象池的时候
            //是不是直接就把对象归还给1号线程的对象池了？
            wife.recycle();
        }, "Thread-1").start();

    }

    static class Wife {

        private static final Recycler<Wife> WIFE = new Recycler<Wife>() {
            @Override
            protected Wife newObject(Handle<Wife> handle) {
                return new Wife(handle);
            }
        };

        static Wife newInstance() {
            //从对象池中获取对象
            return WIFE.get();
        }

        private final Recycler.Handle<Wife> recyclerHandle;

        Wife(Recycler.Handle<Wife> recyclerHandle) {
            this.recyclerHandle = recyclerHandle;
        }

        void recycle() {
            recyclerHandle.recycle(this);
        }
    }
}
