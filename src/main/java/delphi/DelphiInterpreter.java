package delphi;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.*;

public class DelphiInterpreter {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: DelphiInterpreter <source.pas>");
            System.exit(1);
        }

        String filename = args[0];
        CharStream input = CharStreams.fromFileName(filename);
        delphiLexer lexer = new delphiLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        delphiParser parser = new delphiParser(tokens);

        // Custom error listener
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?,?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new RuntimeException("Syntax error at line " + line + ":" + charPositionInLine + " " + msg);
            }
        });

        ParseTree tree = parser.program();
        DelphiVisitor visitor = new DelphiVisitor();
        visitor.visit(tree);
    }
}
