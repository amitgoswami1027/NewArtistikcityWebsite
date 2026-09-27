package com.artistikcity.support;

/** An uploaded file taken from a multipart request (Laravel's UploadedFile). */
public class UploadedFile {

    private final String fieldName;
    private final String originalName;
    private final String contentType;
    private final byte[] content;

    public UploadedFile(String fieldName, String originalName, String contentType, byte[] content) {
        this.fieldName = fieldName;
        this.originalName = originalName;
        this.contentType = contentType;
        this.content = content;
    }

    public String getFieldName() {
        return fieldName;
    }

    /** Client file name, stripped of any path components. */
    public String getClientOriginalName() {
        String n = originalName == null ? "file" : originalName;
        n = n.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        return n.replaceAll("[^A-Za-z0-9._ -]", "_");
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContent() {
        return content;
    }

    public long getSize() {
        return content.length;
    }

    public boolean isEmpty() {
        return content.length == 0 && (originalName == null || originalName.isEmpty());
    }
}
