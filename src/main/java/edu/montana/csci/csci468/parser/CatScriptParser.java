package edu.montana.csci.csci468.parser;

import edu.montana.csci.csci468.parser.expressions.*;
import edu.montana.csci.csci468.parser.statements.*;
import edu.montana.csci.csci468.tokenizer.CatScriptTokenizer;
import edu.montana.csci.csci468.tokenizer.Token;
import edu.montana.csci.csci468.tokenizer.TokenList;
import edu.montana.csci.csci468.tokenizer.TokenType;

import static edu.montana.csci.csci468.tokenizer.TokenType.*;

import java.util.LinkedList;
import java.util.List;

public class CatScriptParser {

    private TokenList tokens;
    private FunctionDefinitionStatement currentFunctionDefinition;

    public CatScriptProgram parse(String source) {
        tokens = new CatScriptTokenizer(source).getTokens();

        // first parse an expression
        CatScriptProgram program = new CatScriptProgram();
        program.setStart(tokens.getCurrentToken());
        Expression expression = null;
        try {
            expression = parseExpression();
        } catch(RuntimeException re) {
            // ignore :)
        }
        if (expression == null || tokens.hasMoreTokens()) {
            tokens.reset();
            while (tokens.hasMoreTokens()) {
                program.addStatement(parseProgramStatement());
            }
        } else {
            program.setExpression(expression);
        }

        program.setEnd(tokens.getCurrentToken());
        return program;
    }

    public CatScriptProgram parseAsExpression(String source) {
        tokens = new CatScriptTokenizer(source).getTokens();
        CatScriptProgram program = new CatScriptProgram();
        program.setStart(tokens.getCurrentToken());
        Expression expression = parseExpression();
        program.setExpression(expression);
        program.setEnd(tokens.getCurrentToken());
        return program;
    }

    //============================================================
    //  Statements
    //============================================================

    private Statement parseProgramStatement() {
        Statement functionDefStatement = parseFunctionDefinitionStatement();
        if(functionDefStatement != null)
        {
            return functionDefStatement;
        }
        return parseStatement();
    }

    private Statement parseFunctionDefinitionStatement() {
        if(tokens.match(FUNCTION))
        {
            Token start = tokens.consumeToken();
            FunctionDefinitionStatement def = new FunctionDefinitionStatement();
            def.setStart(start);
            
            Token functionName = require(IDENTIFIER, def);
            def.setName(functionName.getStringValue());

            require(LEFT_PAREN, def);
            if(!tokens.match(RIGHT_PAREN))
            {
                do{
                    Token parameterName = require(IDENTIFIER, def);
                    TypeLiteral typeLiteral = null;
                    if(tokens.matchAndConsume(COLON))
                    {
                        typeLiteral = parseTypeExpression();
                    }
                    def.addParameter(parameterName.getStringValue(), typeLiteral);
                } while(tokens.matchAndConsume(COMMA) && tokens.hasMoreTokens());
            }
            require(RIGHT_PAREN, def);

            TypeLiteral returnType = null;
            if(tokens.match(COLON)) 
            {
                returnType = parseTypeExpression();
            }
            def.setType(returnType);

            require(LEFT_BRACE, def);
            LinkedList<Statement> statements = new LinkedList<>();
            this.currentFunctionDefinition = def;
            while(!tokens.match(RIGHT_BRACE) && tokens.hasMoreTokens())
            {
                statements.add(parseStatement());
            }
            this.currentFunctionDefinition = null;
            require(RIGHT_BRACE, def);
            def.setBody(statements);
            return def;
        }
        else{
            return null;
        }
    }

    private TypeLiteral parseTypeExpression() {
        TypeLiteral typeLiteral = new TypeLiteral();

        if(tokens.match(INTEGER))
        {
            typeLiteral.setType(new CatscriptType("int", Integer.class));
        }
        if(tokens.match(STRING))
        {
            typeLiteral.setType(new CatscriptType("string", String.class));
        }
        if(tokens.match(TRUE) || tokens.match(FALSE))
        {
            typeLiteral.setType(new CatscriptType("bool", Boolean.class));
        }
        return typeLiteral;
    }

    private Statement parseStatement() {
        Statement stmt = parsePrintStatement();
        if(stmt != null) {
            return stmt;
        }
        stmt = parseForStatement();
        if(stmt != null) {
            return stmt;
        }
        stmt = parseReturnStatement();
        if(stmt != null) {
            return stmt;
        }
        stmt = parseIfStatement();
        if(stmt != null) {
            return stmt;
        }
        stmt = parseVarStatement();
        if(stmt != null) {
            return stmt;
        }
        stmt = parseAssignmentOrFunctionCallStatement();
        if(stmt != null) {
            return stmt;
        }
        if(currentFunctionDefinition != null)
        {
            stmt = parseReturnStatement();
            if(stmt != null)
            {
                return stmt;
            }
        }

        return new SyntaxErrorStatement(tokens.consumeToken());
    }

    private Statement parseAssignmentOrFunctionCallStatement() {
        if(tokens.match(IDENTIFIER))
        {
            Token start = tokens.consumeToken();
            if(tokens.match(EQUAL))
            {
                tokens.consumeToken();
                final AssignmentStatement assignmentStmt = new AssignmentStatement();
                assignmentStmt.setStart(start);
                assignmentStmt.setVariableName(start.getStringValue());
                assignmentStmt.setExpression(parseExpression());
                assignmentStmt.setEnd(tokens.lastToken());
                return assignmentStmt;
            }
            else if(tokens.matchAndConsume(LEFT_PAREN))
            {
                return parseFunctionCallStatement(start);
            }
        }
        return null;
    }

    private Statement parseFunctionCallStatement(Token id) {
        FunctionCallStatement fcs = new FunctionCallStatement(parseFunctionCall(id));
        return fcs;
    }

    private Statement parseVarStatement() {
        if(tokens.match(VAR))
        {
            VariableStatement variableStatement = new VariableStatement();
            variableStatement.setStart(tokens.consumeToken());

            Token id = require(IDENTIFIER, variableStatement);
            variableStatement.setVariableName(id.getStringValue());

            require(COLON, variableStatement);
            TypeLiteral type = parseTypeExpression();
            variableStatement.setExplicitType(type.getType());
            require(EQUAL, variableStatement);
            variableStatement.setExpression(parseExpression());
            return variableStatement;
        }
        return null;
    }

    private Statement parseIfStatement() {
        if(tokens.match(IF))
        {
            IfStatement ifStatement = new IfStatement();
            ifStatement.setStart(tokens.consumeToken());

            require(LEFT_PAREN, ifStatement);
            ifStatement.setExpression(parseExpression());
            require(RIGHT_PAREN, ifStatement);
            require(LEFT_BRACE, ifStatement);
            LinkedList<Statement> ifstatements = new LinkedList<>();
            while(!tokens.match(RIGHT_BRACE) && tokens.hasMoreTokens())
            {
                ifstatements.add(parseStatement());
            }
            require(RIGHT_BRACE, ifStatement);
            ifStatement.setTrueStatements(ifstatements);

            if(tokens.match(ELSE))
            {
                tokens.consumeToken();
                Statement ifelse = parseIfStatement();
                if(ifelse == null)
                {
                    require(LEFT_BRACE, ifStatement);
                    LinkedList<Statement> elsestatements = new LinkedList<>();
                    while(!tokens.match(RIGHT_BRACE) && tokens.hasMoreTokens())
                    {
                        elsestatements.add(parseStatement());
                    }
                    ifStatement.setEnd(require(RIGHT_BRACE, ifStatement));
                    ifStatement.setTrueStatements(elsestatements);
                }
                else if(ifelse != null)
                {
                    //ifStatement.setElseStatements(ifelse);
                }
            }
            return ifStatement;
        }
        return null;
    }

    private Statement parseForStatement() {
        if(tokens.match(FOR))
        {
            ForStatement forStatement = new ForStatement();
            forStatement.setStart(tokens.consumeToken());

            require(LEFT_PAREN, forStatement);
            Token id = require(IDENTIFIER, forStatement);
            forStatement.setVariableName(id.getStringValue());
            require(IN, forStatement);
            forStatement.setExpression(parseExpression());
            require(RIGHT_PAREN, forStatement);

            require(LEFT_BRACE, forStatement);
            LinkedList<Statement> statements = new LinkedList<>();
            while(!tokens.match(RIGHT_BRACE) && tokens.hasMoreTokens())
            {
                statements.add(parseStatement());
            }

            forStatement.setBody(statements);
            forStatement.setEnd(require(RIGHT_BRACE, forStatement));

            return forStatement;
        }
        return null;
    }

    private Statement parseReturnStatement() {
        if(tokens.match(RETURN))
        {
            ReturnStatement returnStatement = new ReturnStatement();
            returnStatement.setStart(tokens.consumeToken());
            returnStatement.setFunctionDefinition(currentFunctionDefinition);

            Expression returnExpression = parseExpression();

            if(returnExpression != null)
            {
                returnStatement.setExpression(returnExpression);
            }

            return returnStatement;

        } else{
            return null;
        }
    }

    private Statement parsePrintStatement() {
        if (tokens.match(PRINT)) {

            PrintStatement printStatement = new PrintStatement();
            printStatement.setStart(tokens.consumeToken());

            require(LEFT_PAREN, printStatement);
            printStatement.setExpression(parseExpression());
            printStatement.setEnd(require(RIGHT_PAREN, printStatement));

            return printStatement;
        } else {
            return null;
        }
    }

    //============================================================
    //  Expressions
    //============================================================

    private Expression parseExpression() {
        return parseEqualityExpression();
    }

    private Expression parseEqualityExpression() {
        Expression expression = parseComparisonExpression();
        while (tokens.match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = tokens.consumeToken();
            final Expression rightHandSide = parseComparisonExpression();
            EqualityExpression equalityExpression = new EqualityExpression(operator, expression, rightHandSide);
            equalityExpression.setStart(expression.getStart());
            equalityExpression.setEnd(rightHandSide.getEnd());
            expression = equalityExpression;
        }
        return expression;
    }

    private Expression parseComparisonExpression() {
        Expression expression = parseAdditiveExpression();
        while (tokens.match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = tokens.consumeToken();
            final Expression rightHandSide = parseAdditiveExpression();
            ComparisonExpression comparisonExpression = new ComparisonExpression(operator, expression, rightHandSide);
            comparisonExpression.setStart(expression.getStart());
            comparisonExpression.setEnd(rightHandSide.getEnd());
            expression = comparisonExpression;
        }
        return expression;
    }

    private Expression parseAdditiveExpression() {
        Expression expression = parseFactorExpression();
        while (tokens.match(PLUS, MINUS)) {
            Token operator = tokens.consumeToken();
            final Expression rightHandSide = parseFactorExpression();
            AdditiveExpression additiveExpression = new AdditiveExpression(operator, expression, rightHandSide);
            additiveExpression.setStart(expression.getStart());
            additiveExpression.setEnd(rightHandSide.getEnd());
            expression = additiveExpression;
        }
        return expression;
    }

    private Expression parseFactorExpression() {
        Expression expression = parseUnaryExpression();
        while (tokens.match(SLASH, STAR)) {
            Token operator = tokens.consumeToken();
            final Expression rightHandSide = parseUnaryExpression();
            FactorExpression factorExpression = new FactorExpression(operator, expression, rightHandSide);
            factorExpression.setStart(expression.getStart());
            factorExpression.setEnd(rightHandSide.getEnd());
            expression = factorExpression;
        }
        return expression;
    }

    private Expression parseUnaryExpression() {
        if (tokens.match(MINUS, NOT)) {
            Token token = tokens.consumeToken();
            Expression rhs = parseUnaryExpression();
            UnaryExpression unaryExpression = new UnaryExpression(token, rhs);
            unaryExpression.setStart(token);
            unaryExpression.setEnd(rhs.getEnd());
            return unaryExpression;
        } else {
            return parsePrimaryExpression();
        }
    }

    private Expression parsePrimaryExpression() {
        if (tokens.match(INTEGER)) {
            Token integerToken = tokens.consumeToken();
            IntegerLiteralExpression integerExpression = new IntegerLiteralExpression(integerToken.getStringValue());
            integerExpression.setToken(integerToken);
            return integerExpression;
        } else if(tokens.match(STRING)){
            Token stringToken = tokens.consumeToken();
            StringLiteralExpression stringExpression = new StringLiteralExpression(stringToken.getStringValue());
            stringExpression.setToken(stringToken);
            return stringExpression;
        } else if(tokens.match(TRUE)){
            Token booleanToken = tokens.consumeToken();
            BooleanLiteralExpression booleanExpression = new BooleanLiteralExpression(true);
            booleanExpression.setToken(booleanToken);
            return booleanExpression;
        } else if(tokens.match(FALSE)){
            Token booleanToken = tokens.consumeToken();
            BooleanLiteralExpression booleanExpression = new BooleanLiteralExpression(false);
            booleanExpression.setToken(booleanToken);
            return booleanExpression;
        } else if(tokens.match(NULL)){
            Token nullToken = tokens.consumeToken();
            NullLiteralExpression nullExpression = new NullLiteralExpression();
            nullExpression.setToken(nullToken);
            return nullExpression;
        } else if(tokens.matchAndConsume(LEFT_PAREN)){
            ParenthesizedExpression paren = new ParenthesizedExpression(parseExpression());
            return paren;
        } else if(tokens.match(LEFT_BRACKET)){
            return parseListLiteral();
        } else if(tokens.match(IDENTIFIER)){
            Token identifier = tokens.consumeToken();
            if(tokens.match(LEFT_PAREN)){
                return parseFunctionCall(identifier);
            } else {
                IdentifierExpression identifierExpression = new IdentifierExpression(identifier.getStringValue());
                identifierExpression.setToken(identifier);
                return identifierExpression;
            }
        }
        else {
            SyntaxErrorExpression syntaxErrorExpression = new SyntaxErrorExpression(tokens.consumeToken());
            return syntaxErrorExpression;
        }
    }

    private FunctionCallExpression parseFunctionCall(Token identifier) {
        List<Expression> fclist = new LinkedList<>();
        if(tokens.matchAndConsume(LEFT_PAREN))
        {
            if(tokens.match(RIGHT_PAREN))
            {
                FunctionCallExpression functionCall = new FunctionCallExpression(identifier.getStringValue(), fclist);
                return functionCall;
            }
            do {
                fclist.add(parseExpression());
            } while(tokens.matchAndConsume(COMMA));
            if(tokens.match(RIGHT_PAREN))
            {
                FunctionCallExpression functionCall = new FunctionCallExpression(identifier.getStringValue(), fclist);
                return functionCall;
            }
            else
            {
                FunctionCallExpression functionCall = new FunctionCallExpression(identifier.getStringValue(), fclist);
                functionCall.addError(ErrorType.UNTERMINATED_ARG_LIST);
                return functionCall;
            }
        }
        return null;
    }

    private Expression parseListLiteral() {
        List<Expression> llist = new LinkedList<>();
        if(tokens.matchAndConsume(LEFT_BRACKET))
        {
            if(tokens.match(RIGHT_BRACKET))
            {
                ListLiteralExpression list = new ListLiteralExpression(llist);
                return list;
            }
            do {
                llist.add(parseExpression());
            } while(tokens.matchAndConsume(COMMA));
            if(tokens.match(RIGHT_BRACKET))
            {
                ListLiteralExpression list = new ListLiteralExpression(llist);
                return list;
            }
            else
            {
                ListLiteralExpression list = new ListLiteralExpression(llist);
                list.addError(ErrorType.UNTERMINATED_LIST);
                return list;
            }
        } else {
            return null;       
        }
    }

    //============================================================
    //  Parse Helpers
    //============================================================
    private Token require(TokenType type, ParseElement elt) {
        return require(type, elt, ErrorType.UNEXPECTED_TOKEN);
    }

    private Token require(TokenType type, ParseElement elt, ErrorType msg) {
        if(tokens.match(type)){
            return tokens.consumeToken();
        } else {
            elt.addError(msg, tokens.getCurrentToken());
            return tokens.getCurrentToken();
        }
    }

}
