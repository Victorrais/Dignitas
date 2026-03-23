package data.models;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class StatementBlock implements ContentBlock{
    /*
    Classe direcionada a fazer a verificação do ContentBlock e formatá-lo para deterinado modelo.
     */

    @Override
    public String toString(){return "FontBlock";}
}
