package src;

import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.*;

/**
 * Created by VE on 2019/9/18.
 */
public class HttpRequestHandler implements Runnable {

    private File rootDir;
    private Socket client;
    private String encoding = "UTF-8";
    private int bufsize = 8192;
    public HttpRequestHandler(Socket socket,File rootDir) {
        this.client = socket;
        this.rootDir = rootDir;
    }

    @Override
    public void run() {
        try{
            Properties parms = new Properties();
            Properties header = new Properties();
            if(client!=null)
            {
                InputStream is = client.getInputStream();
                if(is == null)
                    return;
                byte[] buf = new byte[bufsize];
                int splitbyte = 0;
                int hrlen = 0;
                int read = is.read(buf, 0, bufsize);
                {
                    while (read > 0)
                    {
                        hrlen += read;
                        splitbyte = findHeaderEnd(buf, hrlen);

                        if (splitbyte > 0)
                            break;
                        read = is.read(buf, hrlen, bufsize - hrlen);
                    }
                }
                decodeHeader(buf, parms,header);
//                    if("POST".equalsIgnoreCase(parms.getProperty("method"))){
//                        File dirFile = new File(rootDir,parms.getProperty("uri"));
//                        uploadFile(buf, hrlen, splitbyte, dirFile.getAbsolutePath(), is, parms);
//                    }
                Response r = serveFile(parms.getProperty("uri"), header, rootDir, true);
                if ( r == null )
                    sendError( HTTPStatusCode.HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: Serve() returned a null response." );
                else
                    sendResponse( r.status, r.mimeType, r.header, r.data );

                is.close();
            }
        }catch (Exception e) {
            // TODO: handle exception
            System.out.println("HTTP服务器错误:" + e.getLocalizedMessage());
        }
    }



    /**
     * Common mime types for dynamic content
     */
    public static final String
            MIME_PLAINTEXT = "text/plain",
            MIME_HTML = "text/html",
            MIME_DEFAULT_BINARY = "application/octet-stream",
            MIME_XML = "text/xml";


    /**
     * Returns an error message as a HTTP response and
     * throws InterruptedException to stop further request processing.
     */
    private void sendError( String status, String msg ) throws InterruptedException
    {
        sendResponse(status, MIME_PLAINTEXT, null, new ByteArrayInputStream(msg.getBytes()));
        throw new InterruptedException();
    }

    /**
     * Sends given response to the socket.
     */
    private void sendResponse( String status, String mime, Properties header, InputStream data )
    {
        try
        {
            if ( status == null )
                throw new Error( "sendResponse(): Status can't be null." );

            OutputStream out = client.getOutputStream();
            PrintWriter pw = new PrintWriter( out );
            pw.print("HTTP/1.0 " + status + " \r\n");

            if ( mime != null )
                pw.print("Content-Type: " + mime + "\r\n");

            if ( header == null || header.getProperty( "Date" ) == null )
                pw.print( "Date: " + gmtFrmt.format( new Date()) + "\r\n");

            if ( header != null )
            {
                Enumeration e = header.keys();
                while ( e.hasMoreElements())
                {
                    String key = (String)e.nextElement();
                    String value = header.getProperty( key );
                    pw.print( key + ": " + value + "\r\n");
                }
            }

            pw.print("\r\n");
            pw.flush();

            if ( data != null )
            {
                int pending = data.available();	// This is to support partial sends, see serveFile()
                byte[] buff = new byte[bufsize];
                while (pending>0)
                {
                    int read = data.read( buff, 0, ( (pending>bufsize) ?  bufsize : pending ));
                    if (read <= 0)	break;
                    out.write( buff, 0, read );
                    pending -= read;
                }
            }
            out.flush();
            out.close();
            if ( data != null )
                data.close();
        } catch( IOException ioe )
        {
            // Couldn't write? No can do.
            try { client.close(); } catch( Throwable t ) {}
        }
    }



    private int findHeaderEnd(final byte[] buf, int rlen){
        int splitbyte = 0;
        while (splitbyte + 3 < rlen)
        {
            if (buf[splitbyte] == '\r' && buf[splitbyte + 1] == '\n' && buf[splitbyte + 2] == '\r' && buf[splitbyte + 3] == '\n')
                return splitbyte + 4;
            splitbyte++;
        }
        return 0;
    }
    private void decodeHeader(byte[] buf,Properties parms,Properties header){
        String bufStr = new String(buf);
        String[] bufStrArr = bufStr.split("\r\n");
        if(bufStrArr[0].indexOf(" ") != -1 &&bufStrArr[0].indexOf("HTTP") != -1){
            parms.put("method", bufStrArr[0].substring(0, bufStrArr[0].indexOf(" ")));
            parms.put("uri",bufStrArr[0].subSequence(bufStrArr[0].indexOf(" ")+1, bufStrArr[0].indexOf("HTTP")-1));
        }
        for(String str : bufStrArr){
            if(str.indexOf("boundary")!=-1 && str.indexOf("=") != -1){
                parms.put("boundary", str.substring(str.indexOf("=")+1,str.length()));
            }else if(str.indexOf("Host")!=-1 && str.indexOf(" ") != -1){
                parms.put("host", str.substring(str.indexOf(" ")+1, str.length()));
            }else if(str.indexOf("Content-Length")!=-1 && str.indexOf(" ") != -1){
                parms.put("content-length", str.substring(str.indexOf(" ")+1,str.length()));
            }
            if(str != null && str.trim().length() > 0){
                int p = str.indexOf( ':' );
                if ( p >= 0 ){
                    header.put( str.substring(0,p).trim().toLowerCase(), str.substring(p+1).trim());
                }
            }

        }
    }

    /**
     * Serves file from homeDir and its' subdirectories (only).
     * Uses only URI, ignores all headers and HTTP parameters.
     */
    public Response serveFile( String uri, Properties header, File homeDir,
                               boolean allowDirectoryListing )
    {
        Response res = null;
        try {
            uri = URLDecoder.decode(uri, "utf-8");
            System.out.println("Date: " + gmtFrmt.format( new Date())+ "   " + uri);
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }


        // Make sure we won't die of an exception later
        if ( !homeDir.isDirectory())
            res = new Response( HTTPStatusCode.HTTP_INTERNALERROR, MIME_PLAINTEXT,
                    "INTERNAL ERRROR: serveFile(): given homeDir is not a directory." );

        if ( res == null )
        {
            // Remove URL arguments
            uri = uri.trim().replace( File.separatorChar, '/' );
            if ( uri.indexOf( '?' ) >= 0 )
                uri = uri.substring(0, uri.indexOf( '?' ));

            // Prohibit getting out of current directory
            if ( uri.startsWith( ".." ) || uri.endsWith( ".." ) || uri.indexOf( "../" ) >= 0 )
                res = new Response( HTTPStatusCode.HTTP_FORBIDDEN, MIME_PLAINTEXT,
                        "FORBIDDEN: Won't serve ../ for security reasons." );
        }

        File f = new File( homeDir, uri );
        if ( res == null && !f.exists())
            res = new Response( HTTPStatusCode.HTTP_NOTFOUND, MIME_PLAINTEXT,
                    "Error 404, file not found." );

        // List the directory, if necessary
        if ( res == null && f.isDirectory())
        {
            // Browsers get confused without '/' after the
            // directory, send a redirect.
            if ( !uri.endsWith( "/" ))
            {
                uri += "/";
                res = new Response( HTTPStatusCode.HTTP_REDIRECT, MIME_HTML,
                        "<html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>" +
                                "<script type='text/javascript'>" +
                                "" +
                                "</script>" +
                                "</head><body>Redirected: <a href=\"" + uri + "\">" +
                                uri + "</a></body></html>");
                res.addHeader( "Location", uri );
            }

            if ( res == null )
            {
                // First try index.html and index.htm
                if ( new File( f, "index.html" ).exists())
                    f = new File( homeDir, uri + "/index.html" );
                else if ( new File( f, "index.htm" ).exists())
                    f = new File( homeDir, uri + "/index.htm" );
                    // No index file, list the directory if it is readable
                else if ( allowDirectoryListing && f.canRead() )
                {
                    String[] files = f.list();
                    String msg = "<html><head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>" +
                            HtmlUtil.addJavaScript() +
                            "</head><body><h1>Directory " + uri + "</h1><br/>" +"";
//                            HtmlUtil.addUploadHtml(uri);
                    if ( uri.length() > 1 )
                    {
                        String u = uri.substring( 0, uri.length()-1 );
                        int slash = u.lastIndexOf( '/' );
                        if ( slash >= 0 && slash  < u.length())
                            msg += "<b><a href=\"" + uri.substring(0, slash+1) + "\">..</a></b><br/>";
                    }

                    if (files!=null)
                    {
                        for ( int i=0; i<files.length; ++i )
                        {
                            File curFile = new File( f, files[i] );
                            boolean dir = curFile.isDirectory();
                            if ( dir )
                            {
                                msg += "<b>";
                                files[i] += "/";
                            }

                            msg += "<a href=\"" + encodeUri( uri + files[i] ) + "\">" +
                                    files[i] + "</a>";

                            // Show file size
                            if ( curFile.isFile())
                            {
                                long len = curFile.length();
                                msg += " &nbsp;<font size=2>(";
                                if ( len < 1024 )
                                    msg += len + " bytes";
                                else if ( len < 1024 * 1024 )
                                    msg += len/1024 + "." + (len%1024/10%100) + " KB";
                                else
                                    msg += len/(1024*1024) + "." + len%(1024*1024)/10%100 + " MB";

                                msg += ")</font>";
                            }
                            msg += "<br/>";
                            if ( dir ) msg += "</b>";
                        }
                    }
                    msg += "</body></html>";
                    res = new Response( HTTPStatusCode.HTTP_OK, MIME_HTML, msg );
                }
                else
                {
                    res = new Response( HTTPStatusCode.HTTP_FORBIDDEN, MIME_PLAINTEXT,
                            "FORBIDDEN: No directory listing." );
                }
            }
        }

        try
        {
            if ( res == null )
            {
                // Get MIME type from file name extension, if possible
                String mime = null;
                int dot = f.getCanonicalPath().lastIndexOf( '.' );
                if ( dot >= 0 )
                    mime = (String)theMimeTypes.get( f.getCanonicalPath().substring( dot + 1 ).toLowerCase());
                if ( mime == null )
                    mime = MIME_DEFAULT_BINARY;

                // Calculate etag
                String etag = Integer.toHexString((f.getAbsolutePath() + f.lastModified() + "" + f.length()).hashCode());

                // Support (simple) skipping:
                long startFrom = 0;
                long endAt = -1;
                String range = header.getProperty( "range" );
                if ( range != null )
                {
                    if ( range.startsWith( "bytes=" ))
                    {
                        range = range.substring( "bytes=".length());
                        int minus = range.indexOf( '-' );
                        try {
                            if ( minus > 0 )
                            {
                                startFrom = Long.parseLong( range.substring( 0, minus ));
                                endAt = Long.parseLong( range.substring( minus+1 ));
                            }
                        }
                        catch ( NumberFormatException nfe ) {}
                    }
                }

                // Change return code and add Content-Range header when skipping is requested
                long fileLen = f.length();
                if (range != null && startFrom >= 0)
                {
                    if ( startFrom >= fileLen)
                    {
                        res = new Response( HTTPStatusCode.HTTP_RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "" );
                        res.addHeader( "Content-Range", "bytes 0-0/" + fileLen);
                        if ( mime.startsWith( "application/" ))
                            res.addHeader( "Content-Disposition", "attachment; filename=\"" + f.getName() + "\"");
                        res.addHeader( "ETag", etag);
                    }
                    else
                    {
                        if ( endAt < 0 )
                            endAt = fileLen-1;
                        long newLen = endAt - startFrom + 1;
                        if ( newLen < 0 ) newLen = 0;

                        final long dataLen = newLen;
                        FileInputStream fis = new FileInputStream( f ) {
                            public int available() throws IOException { return (int)dataLen; }
                        };
                        fis.skip( startFrom );

                        res = new Response( HTTPStatusCode.HTTP_PARTIALCONTENT, mime, fis );
                        res.addHeader( "Content-Length", "" + dataLen);
                        res.addHeader( "Content-Range", "bytes " + startFrom + "-" + endAt + "/" + fileLen);
                        if ( mime.startsWith( "application/" ))
                            res.addHeader( "Content-Disposition", "attachment; filename=\"" + f.getName() + "\"");
                        res.addHeader( "ETag", etag);
                    }
                }
                else
                {
                    if (etag.equals(header.getProperty("if-none-match")))
                        res = new Response( HTTPStatusCode.HTTP_NOTMODIFIED, mime, "");
                    else
                    {
                        res = new Response( HTTPStatusCode.HTTP_OK, mime, new FileInputStream( f ));
                        res.addHeader( "Content-Length", "" + fileLen);
                        if ( mime.startsWith( "application/" ))
                            res.addHeader( "Content-Disposition", "attachment; filename=\"" + f.getName() + "\"");
                        res.addHeader( "ETag", etag);
                    }
                }
            }
        }
        catch( IOException ioe )
        {
            res = new Response( HTTPStatusCode.HTTP_FORBIDDEN, MIME_PLAINTEXT, "FORBIDDEN: Reading file failed." );
        }

        res.addHeader( "Accept-Ranges", "bytes"); // Announce that the file server accepts partial content requestes
        return res;
    }

    /**
     * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
     */
    private static Hashtable theMimeTypes = new Hashtable();
    static
    {
        StringTokenizer st = new StringTokenizer(
                "css		text/css "+
                        "htm		text/html "+
                        "html		text/html "+
                        "xml		text/xml "+
                        "txt		text/plain "+
                        "asc		text/plain "+
                        "gif		image/gif "+
                        "jpg		image/jpeg "+
                        "jpeg		image/jpeg "+
                        "png		image/png "+
                        "mp3		audio/mpeg "+
                        "m3u		audio/mpeg-url " +
                        "mp4		video/mp4 " +
                        "ogv		video/ogg " +
                        "flv		video/x-flv " +
                        "mov		video/quicktime " +
                        "swf		application/x-shockwave-flash " +
                        "js		application/javascript "+
                        "pdf		application/pdf "+
                        "doc		application/msword "+
                        "ogg		application/x-ogg "+
                        "zip		application/octet-stream "+
                        "exe		application/octet-stream "+
                        "class		application/octet-stream " );
        while ( st.hasMoreTokens())
            theMimeTypes.put( st.nextToken(), st.nextToken());
    }


    /**
     * URL-encodes everything between "/"-characters.
     * Encodes spaces as '%20' instead of '+'.
     */
    private String encodeUri( String uri )
    {
        String newUri = "";
        StringTokenizer st = new StringTokenizer( uri, "/ ", true );
        while ( st.hasMoreTokens())
        {
            String tok = st.nextToken();
            if ( tok.equals( "/" ))
                newUri += "/";
            else if ( tok.equals( " " ))
                newUri += "%20";
            else
            {
                newUri += URLEncoder.encode(tok);
                // For Java 1.4 you'll want to use this instead:
                // try { newUri += URLEncoder.encode( tok, "UTF-8" ); } catch ( java.io.UnsupportedEncodingException uee ) {}
            }
        }
        return newUri;
    }


    /**
     * GMT date formatter
     */
    private static java.text.SimpleDateFormat gmtFrmt;
    static
    {
        gmtFrmt = new java.text.SimpleDateFormat( "E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
}