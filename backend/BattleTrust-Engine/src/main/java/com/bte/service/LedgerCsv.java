package com.bte.service;
public final class LedgerCsv {
 private LedgerCsv(){}
 public static String cell(Object value){String s=value==null?"":value.toString();String stripped=s.stripLeading();if(!stripped.isEmpty() && "=+-@".indexOf(stripped.charAt(0))>=0)s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
 public static String line(Object... values){return java.util.Arrays.stream(values).map(LedgerCsv::cell).collect(java.util.stream.Collectors.joining(","))+"\r\n";}
}
