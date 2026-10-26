package ru.samara.mailreport;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Read-only URI access for user-requested opening of downloaded mail attachments. */
public final class MailAttachmentProvider extends ContentProvider {
    static final String AUTHORITY="ru.samara.mailreport.attachments";

    static Uri uriFor(File file) {
        return new Uri.Builder().scheme("content").authority(AUTHORITY)
                .appendPath(file.getName()).build();
    }
    static File directory(android.content.Context ctx) {
        File directory=new File(ctx.getCacheDir(),"email-attachments");
        if(!directory.exists())directory.mkdirs();
        return directory;
    }
    private File fileFor(Uri uri) throws FileNotFoundException {
        if(uri==null || !AUTHORITY.equals(uri.getAuthority()))throw new FileNotFoundException();
        String name=uri.getLastPathSegment();
        if(name==null || name.isEmpty() || name.contains("/") || name.contains("\\") || name.equals(".") || name.equals(".."))
            throw new FileNotFoundException("Invalid attachment path");
        try {
            File root=directory(getContext()).getCanonicalFile();
            File file=new File(root,name).getCanonicalFile();
            if(!root.equals(file.getParentFile()) || !file.isFile())throw new FileNotFoundException();
            return file;
        } catch(IOException e){throw new FileNotFoundException("Attachment missing");}
    }
    @Override public boolean onCreate(){return true;}
    @Override public String getType(Uri uri) {
        String filename=uri.getLastPathSegment();
        String suffix=filename==null?"":filename.toLowerCase(java.util.Locale.ROOT);
        if(suffix.endsWith(".xlsx"))return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if(suffix.endsWith(".xls"))return "application/vnd.ms-excel";
        if(suffix.endsWith(".pdf"))return "application/pdf";
        if(suffix.endsWith(".docx"))return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if(suffix.endsWith(".doc"))return "application/msword";
        if(suffix.endsWith(".csv"))return "text/csv";
        if(suffix.endsWith(".txt"))return "text/plain";
        if(suffix.endsWith(".png"))return "image/png";
        if(suffix.endsWith(".jpg")||suffix.endsWith(".jpeg"))return "image/jpeg";
        return "application/octet-stream";
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(fileFor(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder) {
        File file;
        try{file=fileFor(uri);}catch(FileNotFoundException e){return null;}
        if(projection==null)projection=new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE};
        MatrixCursor result=new MatrixCursor(projection);
        Object[] values=new Object[projection.length];
        for(int i=0;i<projection.length;i++){
            if(OpenableColumns.DISPLAY_NAME.equals(projection[i]))values[i]=file.getName().replaceFirst("^[^-]+-","");
            else if(OpenableColumns.SIZE.equals(projection[i]))values[i]=file.length();
            else values[i]=null;
        }
        result.addRow(values);return result;
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){return 0;}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){return 0;}
}
