package com.florentdeborde.mayleo.security;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.*;

public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
    private final byte[] cachedBody;

    public CachedBodyHttpServletRequest(HttpServletRequest request, int maxSize) throws IOException {
        super(request);

        // Limit 2Mo)
        InputStream is = request.getInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        int totalBytes = 0;

        while ((bytesRead = is.read(buffer)) != -1) {
            totalBytes += bytesRead;
            if (totalBytes > maxSize) {
                throw new IOException("Payload too large: exceed " + maxSize + " bytes");
            }
            baos.write(buffer, 0, bytesRead);
        }
        this.cachedBody = baos.toByteArray();
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(this.cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(this.cachedBody)));
    }

    public byte[] getBody() {
        return this.cachedBody;
    }

    private static class CachedBodyServletInputStream extends ServletInputStream {
        private final InputStream cachedBodyInputStream;

        public CachedBodyServletInputStream(byte[] cachedBody) {
            this.cachedBodyInputStream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            try { return cachedBodyInputStream.available() == 0; } catch (IOException e) { return true; }
        }

        @Override
        public boolean isReady() { return true; }

        @Override
        public void setReadListener(ReadListener readListener) { }

        @Override
        public int read() throws IOException { return cachedBodyInputStream.read(); }
    }
}