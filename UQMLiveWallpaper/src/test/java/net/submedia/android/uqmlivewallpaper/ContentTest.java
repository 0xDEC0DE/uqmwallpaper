package net.submedia.android.uqmlivewallpaper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.OperationCanceledException;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class ContentTest extends BaseTest {

    private ContentFixture T;

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        T = null;
    }

    /* ------------------------------------------------------------------------
     * Content Tests
     * -----------------------------------------------------------------------*/
    @Test
    public void testContent() throws IOException {
        T = new ContentFixture();
        try (Content content = T.buildContent(this)) {
            Assert.assertNotNull(content);
            Assert.assertFalse(content.frame.isEmpty());
            Assert.assertEquals(1, content.frame.size());
        }
    }

    @Test
    public void testContent_badContext() throws IOException {
        T = new ContentFixture();
        // Gum up the context object
        when(mockAssetManager.list(anyString())).thenReturn(null);
        // We use the production constructor here to test the error path
        Assert.assertThrows(IOException.class, () -> new Content(new String[]{T.uqmZipFileName}, mockContext, () -> false));
    }

    @Test
    public void testContent_multipleAlienRaces() throws IOException {
        T = new ContentFixture();
        T.setAlienRace("race2");
        T.setup(this);
        // Use a Content subclass that uses the fixture's build() logic to bypass setupContent
        try (Content content = new Content(new String[]{"race1", "race2"}, mockContext, () -> false) {
            @Override
            protected ZipFile setupContent(String zipfile, android.content.Context c) throws IOException {
                return T.build(ContentTest.this).zipfile;
            }
        }) {
            Assert.assertNotNull(content);
            Assert.assertEquals(1, content.frame.size());
        }
    }

    @Test
    public void testContent_noMatch() throws IOException {
        T = new ContentFixture();
        T.setAlienRace("test_race").skipDefaultContent().setup(this);
        Assert.assertThrows(IOException.class, () -> T.build(this));
    }

    @Test
    public void testContent_canceled() throws IOException {
        T = new ContentFixture();
        T.setup(this);
        // Cancel immediately
        Assert.assertThrows(OperationCanceledException.class, () -> new Content(new String[]{"race"}, mockContext, () -> true));
    }

    @Test
    public void testContent_canceledDuringLoad() throws IOException {
        T = new ContentFixture();
        T.setup(this);

        // We need to trigger loadFrames but cancel inside it.
        // The first call to isCancelled.get() is in Content constructor (immediate check),
        // then loadFrames calls it inside the loop.

        java.util.function.Supplier<Boolean> cancelSupplier = mock(java.util.function.Supplier.class);
        // First call (constructor) -> false, Second call (loadFrames) -> true
        when(cancelSupplier.get()).thenReturn(false, true);

        Assert.assertThrows(OperationCanceledException.class, () -> new Content(new String[]{T.params.alienRace()}, mockContext, cancelSupplier) {
            @Override
            protected ZipFile setupContent(String zipfile, android.content.Context c) throws IOException {
                return T.build(ContentTest.this).zipfile;
            }
        });
    }

    @Test
    public void testSetupContent_ioExceptionClosesAfd() throws IOException {
        T = new ContentFixture();
        T.setup(this);

        // Test the production setupContent implementation directly.
        try (MockedStatic<ZipFile> mockedZipFile = mockStatic(ZipFile.class)) {
            ZipFile.Builder mockBuilder = mock(ZipFile.Builder.class);
            mockedZipFile.when(ZipFile::builder).thenReturn(mockBuilder);
            when(mockBuilder.setSeekableByteChannel(any())).thenReturn(mockBuilder);
            when(mockBuilder.get()).thenThrow(new IOException("zip error"));

            // Satisfy AssetManager.list("") so Content constructor proceeds to setupContent
            when(mockAssetManager.list("")).thenReturn(new String[]{T.uqmZipFileName});
            Assert.assertThrows(IOException.class, () -> new Content(new String[]{"race"}, mockContext, () -> false));
            verify(T.mockAfd, atLeastOnce()).close();
        }
    }

    @Test
    public void testSetupContent_ioException_nullAfd() throws IOException {
        T = new ContentFixture();
        // Setup AssetManager to return a file, but openFd to throw exception
        when(mockAssetManager.list("")).thenReturn(new String[]{T.uqmZipFileName});
        when(mockAssetManager.openFd(anyString())).thenThrow(new IOException("openFd failed"));
        Assert.assertThrows(IOException.class, () -> new Content(new String[]{"race"}, mockContext, () -> false));
    }

    @Test
    public void testClose_idempotent() throws IOException {
        T = new ContentFixture();
        Content content = T.buildContent(this);
        content.close();
        content.close();  // Should not throw and remain closed
        Assert.assertNull(content.zipfile);
    }

    @Test
    public void testClose_closesAfd() throws IOException {
        T = new ContentFixture();
        T.setup(this);

        // We must obtain a real ZipFile object before we mock the ZipFile class,
        // otherwise T.build(this) will trigger the mock and return null.
        ZipFile z = T.build(this).zipfile;

        try (MockedStatic<ZipFile> mockedZipFile = mockStatic(ZipFile.class)) {
            ZipFile.Builder mockBuilder = mock(ZipFile.Builder.class);
            mockedZipFile.when(ZipFile::builder).thenReturn(mockBuilder);
            when(mockBuilder.setSeekableByteChannel(any())).thenReturn(mockBuilder);
            when(mockBuilder.get()).thenReturn(z);

            Content c = new Content(new String[]{T.params.alienRace()}, mockContext, () -> false);
            c.close();
            verify(T.mockAfd).close();
            Assert.assertNull(c.zipfile);
        }
    }

    @Test
    public void testClose_recycledBitmaps() throws IOException {
        T = new ContentFixture();
        try (Content content = T.buildContent(this)) {
            Bitmap bitmap = content.frame.get(0).content;
            when(bitmap.isRecycled()).thenReturn(true);
            content.close();
            verify(bitmap, Mockito.never()).recycle();
        }
    }

    @Test
    public void testClose_nullBitmap() throws Exception {
        T = new ContentFixture();
        Content c = T.buildContent(this);
        // Use reflection to set a frame with null bitmap
        Content.Frame frame = c.frame.get(0);
        java.lang.reflect.Field field = Content.Frame.class.getDeclaredField("content");
        field.setAccessible(true);

        // We can't easily change a final field, but we can add a new frame to the list
        c.frame.clear();
        Content.Frame mockFrame = mock(Content.Frame.class);
        c.frame.add(mockFrame);

        c.close();  // Should not crash
    }

    @Test
    public void testAssetMatching() throws IOException {
        String match = "." + createString(rand.nextInt(1, 5));
        String first = createString() + match;
        String second = createString() + match;
        String[] items = {createString(), first, createString(), second};
        String result = Content.assetMatching(match, items);
        Assert.assertEquals(first, result);
    }

    @Test
    public void testAssetMatching_noMatch() {
        String match = "." + createString(rand.nextInt(1, 5));
        String[] items = {createString(), createString(), createString()};
        Assert.assertThrows(IOException.class, () -> Content.assetMatching(match, items));
    }

    @Test
    public void testAniToFileList() throws IOException {
        T = new ContentFixture();
        String alienRace = createString();
        String aniFilename = alienRace + ".ani";
        String pngFilename1 = createString() + ".png";
        String pngFilename2 = createString() + ".png";
        Coord hotspot = new Coord(rand.nextInt(-100, 100), rand.nextInt(-100, 100));
        Coord size = new Coord(rand.nextInt(320), rand.nextInt(240));
        String fakeAni = " %d %d %d %d".formatted(size.x(), size.y(), hotspot.x(), hotspot.y());

        Map<String, byte[]> zipContents = new HashMap<>();
        String aniFile = "base/comm/%s/%s".formatted(alienRace, aniFilename);
        String pngFile1 = "base/comm/%s/%s".formatted(alienRace, pngFilename1);
        String pngFile2 = "base/comm/%s/%s".formatted(alienRace, pngFilename2);
        zipContents.put(aniFile, createAniContent(List.of(pngFilename1 + fakeAni, pngFilename2 + fakeAni)));
        zipContents.put(pngFile1, createPngContent());
        zipContents.put(pngFile2, createPngContent());

        T.setAlienRace(alienRace)
                .setPngFilename(pngFilename1)
                .setAniFilename(aniFilename)
                .setHotspot(hotspot)
                .setSize(size);
        T.createAndSetZipFile(zipContents);
        T.setup(this);

        try (Content content = T.build(this)) {
            List<String> result = content.aniToFileList(aniFile);
            Assert.assertNotNull(result);
            Assert.assertTrue(result.contains(pngFile1 + fakeAni));
            Assert.assertTrue(result.contains(pngFile2 + fakeAni));
        }
    }

    @Test
    public void testReadFromContentPack() throws IOException {
        T = new ContentFixture();
        byte[] result;
        try (Content content = T.buildContent(this)) {
            result = content.readFromContentPack(T.getContentBaseDir() + T.params.pngFilename());
        }
        Assert.assertNotNull(result);
        Assert.assertArrayEquals(createPngContent(), result);
    }

    @Test
    public void testReadFromContentPack_entryNotFound() throws IOException {
        T = new ContentFixture();
        String filename = createString();
        try (Content content = T.buildContent(this)) {
            Assert.assertThrows(IOException.class, () -> content.readFromContentPack(filename));
        }
    }

    @Test
    public void testContent_toString() throws IOException {
        T = new ContentFixture();
        // Positive-only hotspots to keep the tests simpler
        Coord hotspot = new Coord(rand.nextInt(100), rand.nextInt(100));
        try (Content content = T.setHotspot(hotspot).buildContent(this)) {
            String expected = """
                %s Object {
                 Frame:
                  Filename: %s
                  Hotspot: (%1.2f, %1.2f)
                  %s
                }""".formatted(content.getClass().getName(), T.getContentBaseDir() + T.params.pngFilename(), (float) hotspot.x(), (float) hotspot.y(), content.frame.get(0));

            Assert.assertEquals(expected, content.toString());
        }
    }

    /* ------------------------------------------------------------------------
     * Content.Frame Tests
     * -----------------------------------------------------------------------*/

    @Test
    public void testFrame() throws IOException {
        T = new ContentFixture();
        String alienRace = createString();
        String pngFilename = createString() + ".png";
        Coord hotspot = new Coord(rand.nextInt(100), rand.nextInt(100));

        Content.Frame frame;
        try (Content content = T.setAlienRace(alienRace)
                .setPngFilename(pngFilename)
                .setHotspot(hotspot)
                .buildContent(this)) {
            frame = content.frame.get(0);
        }

        Assert.assertEquals("base/comm/%s/%s".formatted(alienRace, pngFilename), frame.filename);
        Assert.assertEquals((float) hotspot.x(), frame.hotspot.x(), 0);
        Assert.assertEquals((float) hotspot.y(), frame.hotspot.y(), 0);
    }

    @Test
    public void testFrame_missingZipEntry() throws IOException {
        T = new ContentFixture();
        String alienRace = createString();
        String pngFilename = createString() + ".png";
        String aniFilename = createString() + ".ani";
        Coord hotspot = new Coord(rand.nextInt(-100, 100), rand.nextInt(-100, 100));
        Coord size = new Coord(rand.nextInt(320), rand.nextInt(240));
        String fakeAni = " %d %d %d %d".formatted(size.x(), size.y(), hotspot.x(), hotspot.y());

        Map<String, byte[]> zipContents = new HashMap<>();
        String aniFile = "base/comm/%s/%s".formatted(alienRace, aniFilename);
        zipContents.put(aniFile, createAniContent(List.of(pngFilename + fakeAni)));

        T.setAlienRace(alienRace)
                .setPngFilename(pngFilename)
                .setAniFilename(aniFilename)
                .setHotspot(hotspot)
                .setSize(size);
        T.skipDefaultContent();
        T.createAndSetZipFile(zipContents);
        T.setup(this);

        Assert.assertThrows(IOException.class, () -> T.build(this));
    }

    @Test
    public void testFrame_failedBitmapDecoding() throws IOException {
        T = new ContentFixture();
        T.setup(this);

        // Re-stub the existing static mock instead of creating a new one
        mockedStaticBitmapFactory.when(() -> BitmapFactory.decodeStream(any(InputStream.class), isNull(), any(BitmapFactory.Options.class))).thenReturn(null);
        Assert.assertThrows(IOException.class, () -> T.build(this));
    }

    @Test
    public void testFrame_toString() throws IOException {
        T = new ContentFixture();
        Coord size = new Coord(rand.nextInt(320), rand.nextInt(240));

        Content.Frame frame;
        try (Content content = T.setSize(size).buildContent(this)) {
            frame = content.frame.get(0);
        }
        String expected = "Content: %dx%d".formatted(size.x(), size.y());
        Assert.assertEquals(expected, frame.toString());
    }

    /* ------------------------------------------------------------------------
     * Content.Frame.Hotspot Tests
     * -----------------------------------------------------------------------*/

    @Test
    public void testHotspot_float() {
        float x = rand.nextFloat() * 1024;
        float y = rand.nextFloat() * 1024;
        Content.Frame.Hotspot hotspot = new Content.Frame.Hotspot(x, y);
        Assert.assertEquals(x, hotspot.x(), 0);
        Assert.assertEquals(y, hotspot.y(), 0);
    }

    @Test
    public void testHotspot_float_inverts_negatives() {
        float x = rand.nextFloat() * 1024;
        float y = rand.nextFloat() * 1024;
        Content.Frame.Hotspot hotspot = new Content.Frame.Hotspot(-x, -y);
        Assert.assertEquals(x, hotspot.x(), 0);
        Assert.assertEquals(y, hotspot.y(), 0);
    }

    @Test
    public void testHotspot_int() {
        int x = rand.nextInt(1024);
        int y = rand.nextInt(1024);
        Content.Frame.Hotspot hotspot = new Content.Frame.Hotspot(x, y);
        Assert.assertEquals(x, hotspot.x(), 0);
        Assert.assertEquals(y, hotspot.y(), 0);
    }

    @Test
    public void testHotspot_string() {
        int x = rand.nextInt(1024);
        int y = rand.nextInt(1024);
        Content.Frame.Hotspot hotspot = new Content.Frame.Hotspot("%d".formatted(x), "%d".formatted(y));
        Assert.assertEquals(x, hotspot.x(), 0);
        Assert.assertEquals(y, hotspot.y(), 0);
    }

    @Test
    public void testHotspot_intString() {
        int x = rand.nextInt(1024);
        int y = rand.nextInt(1024);
        Content.Frame.Hotspot hotspot = new Content.Frame.Hotspot("%d".formatted(x), y);
        Assert.assertEquals(x, hotspot.x(), 0);
        Assert.assertEquals(y, hotspot.y(), 0);
    }

    @Test
    public void testHotspot_stringInt() {
        int x = rand.nextInt(1024);
        int y = rand.nextInt(1024);
        Content.Frame.Hotspot hotspot = new Content.Frame.Hotspot(x, "%d".formatted(y));
        Assert.assertEquals(x, hotspot.x(), 0);
        Assert.assertEquals(y, hotspot.y(), 0);
    }

    @Test
    public void testHotspot_toString() {
        float x = rand.nextFloat() * 1024f;
        float y = rand.nextFloat() * 1024f;
        Content.Frame.Hotspot hotspot = new Content.Frame.Hotspot(x, y);
        Assert.assertEquals("Hotspot: (%.2f, %.2f)".formatted(x, y), hotspot.toString());
    }

    /* ------------------------------------------------------------------------
     * Content.BoundedSeekableByteChannel Tests
     * -----------------------------------------------------------------------*/

    @Test
    public void testBoundedChannel_read() throws IOException {
        int dataSize = rand.nextInt(100, 1000);
        byte[] data = createString(dataSize).getBytes();
        rand.nextBytes(data);

        int offset = rand.nextInt(dataSize / 2);
        int size = rand.nextInt(1, dataSize - offset);

        try (var channel = new SeekableInMemoryByteChannel(data);
             var bounded = new Content.BoundedSeekableByteChannel(channel, offset, size)) {
            ByteBuffer buf = ByteBuffer.allocate(size);
            Assert.assertEquals(size, bounded.read(buf));
            Assert.assertArrayEquals(Arrays.copyOfRange(data, offset, offset + size), buf.array());
            Assert.assertEquals(size, bounded.position());
        }
    }

    @Test
    public void testBoundedChannel_read_partial() throws IOException {
        int dataSize = rand.nextInt(100, 1000);
        byte[] data = createString(dataSize).getBytes();
        rand.nextBytes(data);

        int offset = rand.nextInt(dataSize / 2);
        int size = rand.nextInt(20, dataSize - offset);

        try (var channel = new SeekableInMemoryByteChannel(data);
             var bounded = new Content.BoundedSeekableByteChannel(channel, offset, size)) {
            int firstRead = rand.nextInt(1, size);
            ByteBuffer buf = ByteBuffer.allocate(firstRead);
            Assert.assertEquals(firstRead, bounded.read(buf));

            int remaining = size - firstRead;
            buf = ByteBuffer.allocate(remaining + 10);
            Assert.assertEquals(remaining, bounded.read(buf));
            Assert.assertEquals(-1, bounded.read(buf));
        }
    }

    @Test
    public void testBoundedChannel_read_emptyBuffer() throws IOException {
        try (SeekableByteChannel mockChannel = mock(SeekableByteChannel.class);
             var bounded = new Content.BoundedSeekableByteChannel(mockChannel, 0, 10)) {
            ByteBuffer buf = ByteBuffer.allocate(0);
            Assert.assertEquals(-1, bounded.read(buf));
        }
    }

    @Test
    public void testBoundedChannel_read_nothingRead() throws IOException {
        try (SeekableByteChannel mockChannel = mock(SeekableByteChannel.class);
             var bounded = new Content.BoundedSeekableByteChannel(mockChannel, 0, 10)) {
            when(mockChannel.read(any(ByteBuffer.class))).thenReturn(0);
            ByteBuffer buf = ByteBuffer.allocate(5);
            Assert.assertEquals(0, bounded.read(buf));
            Assert.assertEquals(0, bounded.position());
        }
    }

    @Test
    public void testBoundedChannel_read_innerEof() throws IOException {
        try (SeekableByteChannel mockChannel = mock(SeekableByteChannel.class);
             var bounded = new Content.BoundedSeekableByteChannel(mockChannel, 0, 10)) {
            when(mockChannel.read(any(ByteBuffer.class))).thenReturn(-1);
            ByteBuffer buf = ByteBuffer.allocate(5);
            Assert.assertEquals(-1, bounded.read(buf));
            Assert.assertEquals(0, bounded.position());
        }
    }

    @Test
    public void testBoundedChannel_seek() throws IOException {
        int dataSize = rand.nextInt(100, 1000);
        byte[] data = createString(dataSize).getBytes();
        rand.nextBytes(data);

        int offset = rand.nextInt(dataSize / 2);
        int size = rand.nextInt(10, dataSize - offset);

        try (var channel = new SeekableInMemoryByteChannel(data);
             var bounded = new Content.BoundedSeekableByteChannel(channel, offset, size)) {
            int pos = rand.nextInt(size);
            bounded.position(pos);
            ByteBuffer buf = ByteBuffer.allocate(1);
            bounded.read(buf);
            Assert.assertEquals(data[offset + pos], buf.get(0));
            Assert.assertEquals(pos + 1, bounded.position());
        }
    }

    @Test
    public void testBoundedChannel_position_outOfBounds() throws IOException {
        int offset = rand.nextInt(100);
        int size = rand.nextInt(10, 100);
        try (SeekableByteChannel mockChannel = mock(SeekableByteChannel.class);
             Content.BoundedSeekableByteChannel bounded = new Content.BoundedSeekableByteChannel(mockChannel, offset, size)) {
            Assert.assertThrows(IllegalArgumentException.class, () -> bounded.position(-1));
            Assert.assertThrows(IllegalArgumentException.class, () -> bounded.position(size + 1));
        }
    }

    @Test
    public void testBoundedChannel_size() throws IOException {
        int size = rand.nextInt(10, 1000);
        try (SeekableByteChannel mockChannel = mock(SeekableByteChannel.class);
             Content.BoundedSeekableByteChannel bounded = new Content.BoundedSeekableByteChannel(mockChannel, 0, size)) {
            Assert.assertEquals(size, bounded.size());
        }
    }

    @Test
    public void testBoundedChannel_write_unsupported() throws IOException {
        try (SeekableByteChannel mockChannel = mock(SeekableByteChannel.class);
             Content.BoundedSeekableByteChannel bounded = new Content.BoundedSeekableByteChannel(mockChannel, 0, 10)) {
            Assert.assertThrows(NonWritableChannelException.class, () -> bounded.write(ByteBuffer.allocate(1)));
        }
    }

    @Test
    public void testBoundedChannel_truncate_unsupported() throws IOException {
        try (SeekableByteChannel mockChannel = mock(SeekableByteChannel.class);
             Content.BoundedSeekableByteChannel bounded = new Content.BoundedSeekableByteChannel(mockChannel, 0, 10)) {
            Assert.assertThrows(NonWritableChannelException.class, () -> bounded.truncate(5));
        }
    }

    @Test
    public void testBoundedChannel_close() throws IOException {
        SeekableByteChannel mockChannel = mock(SeekableByteChannel.class);
        when(mockChannel.isOpen()).thenReturn(true);
        var bounded = new Content.BoundedSeekableByteChannel(mockChannel, 0, 10);
        Assert.assertTrue(bounded.isOpen());
        bounded.close();
        verify(mockChannel).close();
    }
}
