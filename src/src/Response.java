package src;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Properties;

public class Response {





    /**
     * Default constructor: response = HTTP_OK, data = mime = 'null'
     */
    public Response()
    {
        this.status = HTTPStatusCode.HTTP_OK;
    }

    /**
     * Basic constructor.
     */
    public Response( String status, String mimeType, InputStream data )
    {
        this.status = status;
        this.mimeType = mimeType;
        this.data = data;
    }

    /**
     * Convenience method that makes an InputStream out of
     * given text.
     */
    public Response( String status, String mimeType, String txt )
    {
        this.status = status;
        this.mimeType = mimeType;
        try
        {
            this.data = new ByteArrayInputStream( txt.getBytes("UTF-8"));
        }
        catch ( java.io.UnsupportedEncodingException uee )
        {
            uee.printStackTrace();
        }
    }

    /**
     * Adds given line to the header.
     */
    public void addHeader( String name, String value )
    {
        header.put( name, value );
    }

    /**
     * HTTP status code after processing, e.g. "200 OK", HTTP_OK
     */
    public String status;

    /**
     * MIME type of content, e.g. "text/html"
     */
    public String mimeType;

    /**
     * Data of the response, may be null.
     */
    public InputStream data;

    /**
     * Headers for the HTTP response. Use addHeader()
     * to add lines.
     */
    public Properties header = new Properties();
}