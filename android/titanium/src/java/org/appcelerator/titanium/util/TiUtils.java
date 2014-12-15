package org.appcelerator.titanium.util;

public class TiUtils {
    public static String fastReplace(String source, String os, String ns) {
        if (source == null) {
            return null;
        }
        int i = 0;
        if ((i = source.indexOf(os, i)) >= 0) {
            char[] sourceArray = source.toCharArray();
            char[] nsArray = ns.toCharArray();
            int oLength = os.length();
            StringBuilder buf = new StringBuilder(sourceArray.length);
            buf.append(sourceArray, 0, i).append(nsArray);
            i += oLength;
            int j = i;
            // Replace all remaining instances of oldString with newString.
            while ((i = source.indexOf(os, i)) > 0) {
                buf.append(sourceArray, j, i - j).append(nsArray);
                i += oLength;
                j = i;
            }
            buf.append(sourceArray, j, sourceArray.length - j);
            source = buf.toString();
            buf.setLength(0);
        }
        return source;
    }
    
    public static String[] fastSplit(String s, char delimeter) {
        
        int count = 1;
 
        for (int i = 0; i < s.length(); i++)
            if (s.charAt(i) == delimeter)
                count++;
 
        String[] array = new String[count];
 
        int a = -1;
        int b = 0;
 
        for (int i = 0; i < count; i++) {
 
            while (b < s.length() && s.charAt(b) != delimeter)
                b++;
 
            array[i] = s.substring(a+1, b);
            a = b;
            b++;
            
        }
 
        return array;
 
    }
}
