package src;

public class HtmlUtil {
    public static String addJavaScript(){
        String js ="<script type=\"text/javascript\">" +
                "\tfunction uploadfiles(){\n" +
                "\t\tvar files = document.getElementById(\"files\").files;\n" +
                "\t\tvar url = window.location.href; \n" +
                "\t\tdocument.getElementById(\"uploading\").style.display=\"block\";\n" +
                "\t\tdocument.getElementById(\"uploaded\").style.display=\"block\";\n" +
                "\t\tvar i=0;\n" +
                "\t\tuploadfile(files,url,i);\n" +
                "\t}\n" +
                "\n" +
                "function uploadfile(files,url,i){\n" +
                "\t\tif(i<files.length){\n" +
                "\t\t\tvar file = files[i];\n" +
                "\t\t\tdocument.getElementById(\"uploading\").innerHTML = \"<p>\"+file.name+\" is uploading...\";\n" +
                "\t\t\tvar form = new FormData();\n" +
                "\t\t\tform.append(\"file\",file);\n" +
                "\t\t\tvar xhr = new XMLHttpRequest();\n" +
                "\t\t\txhr.open(\"post\",url,true);\n" +
                "\t\t\txhr.send(form);\n" +
                "\t\t\txhr.onreadystatechange = function(){\n" +
                "\t\t\t\tif(xhr.readyState == 4){\n" +
                "\t\t\t\t\tif(xhr.status == 200){\n" +
                "\t\t\t\t\t\tdocument.getElementById(\"uploading\").innerHTML = \"<p>\"+file.name+\" has been uploaded.\"+\"</p>\";\n" +
                "\t\t\t\t\t\tdocument.getElementById(\"uploaded\").innerHTML += \"<br>\"+file.name;\n" +
                "\t\t\t\t\t\txhr = null;\n" +
                "\t\t\t\t\t\tuploadfile(files,url,i+1);\n" +
                "\t\t\t\t\t}else{\n" +
                "\t\t\t\t\t\txhr = null;\n" +
                "\t\t\t\t\t\tuploadfile(files,url,i);\n" +
                "\t\t\t\t\t}\n" +
                "\t\t\t\t}\n" +
                "\t\t\t}\n" +
                "\t\t}else{\n" +
                "\t\t\tdocument.getElementById(\"uploading\").innerHTML=\"<p>all \"+files.length+\" files have been uploaded.</p>\";\n" +
                "\t\t\talert(\"All \"+files.length+\" files has been uploaded.\");\n" +
                "\t\t}\n" +
                "\t}" +
                "</script>";
        return js;
    }
    public static  String addUploadHtml(String uri){
        String uploadHtml = "<form action=\"";
        uploadHtml += uri;
        uploadHtml += "\" enctype=\"multipart/form-data\" method=\"post\">\n" +
                "      File: <input id='files' type=\"file\" multiple=\"multiple\" name=\"datafile\" size=\"40\"><br>\n" +
                "    <input type='button' id=\"submit\" onclick=\"uploadfiles()\" value='submit'>\n" +
                "   </form>"+
                "<p id='uploading' style='display:none'></p>"+
                "<p id='uploaded'  style='display:none'>uploaded files list</p>";
        return  uploadHtml;
    }

}