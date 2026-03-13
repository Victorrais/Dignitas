package data.models;

public class FontBlock implements ContentBlock{
    /*
    Classe direcionada a fazer a verificação do ContentBlock e formata-lo para deterinado modelo.
     */
    @Override
    public String toString(){return "FontBlock";}

    @Override
    public String blockFormat(String input) {
        return "";
    }

    @Override
    public String blockFormat() {
        return "";
    }
    /*
    @Override
    public String toString(){return this.getClass().getName();}
     */
}
