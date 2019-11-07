package com.meijie.server.store;

import com.meijie.store.BigFileStore;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class BigFileStoreTest {

    private BigFileStore bigFileStore;

    @Before
    public void init() {
        bigFileStore = new BigFileStore("/home/meijie/Videos/");
    }

    @After
    public void destry() throws Exception {
        bigFileStore.close();
//        File imageFile = new File("/home/meijie/Videos/IMAGE_STORE");
//        imageFile.delete();

//        File indexFile = new File("/home/meijie/Videos/IMAGE_STORE_INDEX");
//        indexFile.delete();
    }

    @Test
    public void testSaveFile() {
        byte[] b = generateByteBuffer(10);
        writeReadCompareByteArray(b);
        byte[] b2 = generateByteBuffer(4);
        writeReadCompareByteArray(b2);
    }

    private byte[] generateByteBuffer(int count) {
        byte[] b = new byte[count];
        for (int i = 0; i < b.length; i++) {
            b[i] = (byte) i;
        }
        return b;
    }

    private void writeReadCompareByteArray(byte[] b) {
        long fileId = bigFileStore.storeBigFile(b);
        byte[] b2 = bigFileStore.readFile(fileId);

        Assert.assertEquals(b.length, b2.length);
        for (int i = 0; i < b.length; i++) {
            Assert.assertEquals(b[i], b2[i]);
        }
    }

}
