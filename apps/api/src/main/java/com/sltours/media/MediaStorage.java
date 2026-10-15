package com.sltours.media;
import java.io.*;

/** Storage backend for uploaded files. Implementations must never expose files publicly on their own. */
public interface MediaStorage {
    void write(String key, InputStream content) throws IOException;
    InputStream read(String key) throws IOException;
    void delete(String key) throws IOException;
}
