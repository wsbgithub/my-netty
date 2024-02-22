package com.pp.netty.test.objectpool;

public class Book{
    //表示是第几本书，当然，对象池中的对象应该是一样的，但这里我们添加一个多余的属性
    //是为了将对象池中的问题很好地暴露出来
    public String index;

    //表示书翻到第几页了，刚从对象池中拿出来时，默认是在第0页
    private int pageNow = 0;

    //默认书的最后一页为第500页
    private int pageEnd = 500;

    public Book() {

    }

    public Book(String index) {
        this.index = index;
    }

    //翻页的方法
    public void pageTurn() {
        while (pageNow <= pageEnd) {
            pageNow++;
            System.out.println("这本书翻到第"+pageNow+"页了！");
        }
        System.out.println("这本书已经翻到最后一页了！");
    }


    //把书页重置到第0页
    public void resetPage() {
        pageNow = 0;
    }


    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public int getPageNow() {
        return pageNow;
    }
}
