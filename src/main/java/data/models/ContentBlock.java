package data.models;

public interface ContentBlock {
    String text=null;

    public String blockFormat(String input);
    public String blockFormat();
}
