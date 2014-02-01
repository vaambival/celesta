package ru.curs.celesta.score;

import java.util.List;

/** Скалярное выражение SQL. */
public abstract class Expr {
	private final View v;

	Expr(View v) {
		this.v = v;
	}

	final View getView() {
		return v;
	}

	final void assertType(ExprType t) throws ParseException {
		if (getType() != t)
			throw new ParseException(
					String.format(
							"Expression '%s' is expected to be of %s type, but it is %s",
							getCSQL(), t.toString(), getType().toString()));
	}

	/**
	 * Возвращает Celesta-SQL представление выражения.
	 */
	public abstract String getCSQL();

	/**
	 * Возвращает тип выражения.
	 */
	public abstract ExprType getType();

	/**
	 * Разрешает ссылки на поля таблиц, используя контекст объявленных таблиц с
	 * их псевдонимами.
	 * 
	 * @param tables
	 *            перечень таблиц.
	 * @throws ParseException
	 *             в случае, если ссылка не может быть разрешена.
	 */
	public abstract void resolveFieldRefs(List<TableRef> tables)
			throws ParseException;

	/**
	 * Проверяет типы всех входящих в выражение субвыражений.
	 * 
	 * @throws ParseException
	 *             в случае, если контроль типов не проходит.
	 */
	public abstract void validateTypes() throws ParseException;

	/**
	 * Принимает посетителя при обходе дерева разбора для решения задач контроля
	 * типов, кодогенерации и проч.
	 * 
	 * @param visitor
	 *            Универсальный элемент visitor, выполняющий задачи с деревом
	 *            разбора.
	 */
	public abstract void accept(ExprVisitor visitor);
}

/**
 * Тип выражения.
 */
enum ExprType {
	LOGIC, NUMERIC, TEXT, DATE, BIT, BLOB, UNDEFINED
}

/**
 * Выражение в скобках.
 */
final class ParenthesizedExpr extends Expr {
	private final Expr parenthesized;

	public ParenthesizedExpr(View v, Expr parenthesized) {
		super(v);
		this.parenthesized = parenthesized;
	}

	/**
	 * Возвращает выражение, заключенное в скобки.
	 */
	public Expr getParenthesized() {
		return parenthesized;
	}

	@Override
	public String getCSQL() {
		return "(" + parenthesized.getCSQL() + ")";
	}

	@Override
	public ExprType getType() {
		return parenthesized.getType();
	}

	@Override
	public void validateTypes() throws ParseException {
		parenthesized.validateTypes();
	}

	@Override
	public void resolveFieldRefs(List<TableRef> tables) throws ParseException {
		parenthesized.resolveFieldRefs(tables);
	}

	@Override
	public void accept(ExprVisitor visitor) {
		parenthesized.accept(visitor);
		visitor.visitParenthesizedExpr(this);
	}
}

/**
 * Отношение (>=, <=, <>, =, <, >).
 */
final class Relop extends Expr {
	/**
	 * >.
	 */
	public static final int GT = 0;
	/**
	 * <.
	 */
	public static final int LS = 1;
	/**
	 * >=.
	 */
	public static final int GTEQ = 2;
	/**
	 * <=.
	 */
	public static final int LSEQ = 3;
	/**
	 * <>.
	 */
	public static final int NTEQ = 4;
	/**
	 * =.
	 */
	public static final int EQ = 5;

	/**
	 * LIKE.
	 */
	public static final int LIKE = 6;

	private static final String[] OPS = { " > ", " < ", " >= ", " <= ", " <> ",
			" = ", " LIKE " };

	private final Expr left;
	private final Expr right;
	private final int relop;

	public Relop(View v, Expr left, Expr right, int relop) {
		super(v);
		if (relop < 0 || relop >= OPS.length)
			throw new IllegalArgumentException();
		this.left = left;
		this.right = right;
		this.relop = relop;
	}

	/**
	 * Левая сторона.
	 */
	public Expr getLeft() {
		return left;
	}

	/**
	 * Правая сторона.
	 */
	public Expr getRight() {
		return right;
	}

	/**
	 * Отношение.
	 */
	public int getRelop() {
		return relop;
	}

	@Override
	public String getCSQL() {
		return left.getCSQL() + OPS[relop] + right.getCSQL();
	}

	@Override
	public ExprType getType() {
		return ExprType.LOGIC;
	}

	@Override
	public void validateTypes() throws ParseException {
		left.validateTypes();
		right.validateTypes();

		ExprType t = left.getType();
		// Сравнивать можно не все типы.
		if (t == ExprType.DATE || t == ExprType.NUMERIC || t == ExprType.TEXT) {
			// сравнивать можно только однотипные термы
			right.assertType(t);
			// при этом like действует только на строковых термах
			if (relop == LIKE)
				left.assertType(ExprType.TEXT);
		} else {
			throw new ParseException(
					String.format(
							"Wrong expression '%s': type %s cannot be used in comparisions.",
							getCSQL(), t.toString()));
		}
	}

	@Override
	public void resolveFieldRefs(List<TableRef> tables) throws ParseException {
		left.resolveFieldRefs(tables);
		right.resolveFieldRefs(tables);
	}

	@Override
	public void accept(ExprVisitor visitor) {
		left.accept(visitor);
		right.accept(visitor);
		visitor.visitRelop(this);
	}
}

/**
 * ... IN (..., ..., ...).
 */
final class In extends Expr {
	private final Expr left;
	private final List<Expr> operands;

	In(View v, Expr left, List<Expr> operands) {
		super(v);
		this.operands = operands;
		this.left = left;
	}

	/**
	 * Оператор.
	 */
	public Expr getLeft() {
		return left;
	}

	/**
	 * Операнды.
	 */
	public List<Expr> getOperands() {
		return operands;
	}

	@Override
	public String getCSQL() {
		StringBuilder result = new StringBuilder(left.getCSQL());
		result.append(" IN (");
		boolean needComma = false;
		for (Expr operand : operands) {
			if (needComma)
				result.append(", ");
			result.append(operand.getCSQL());
			needComma = true;
		}
		result.append(")");
		return result.toString();
	}

	@Override
	public ExprType getType() {
		return ExprType.LOGIC;
	}

	@Override
	public void validateTypes() throws ParseException {
		left.validateTypes();
		for (Expr operand : operands)
			operand.validateTypes();

		ExprType t = left.getType();
		// Сравнивать можно не все типы.
		if (t == ExprType.DATE || t == ExprType.NUMERIC || t == ExprType.TEXT) {
			// все операнды должны быть однотипны
			for (Expr operand : operands) {
				operand.assertType(t);
			}
		} else {
			throw new ParseException(
					String.format(
							"Wrong expression '%s': type %s cannot be used in ...IN(...) expression.",
							getCSQL(), t.toString()));
		}
	}

	@Override
	public void resolveFieldRefs(List<TableRef> tables) throws ParseException {
		left.resolveFieldRefs(tables);
		for (Expr operand : operands)
			operand.resolveFieldRefs(tables);
	}

	@Override
	public void accept(ExprVisitor visitor) {
		left.accept(visitor);
		for (Expr operand : operands)
			operand.accept(visitor);
		visitor.visitIn(this);
	}

}

/**
 * BETWEEN.
 */
final class Between extends Expr {
	private final Expr left;
	private final Expr right1;
	private final Expr right2;

	public Between(View v, Expr left, Expr right1, Expr right2) {
		super(v);
		this.left = left;
		this.right1 = right1;
		this.right2 = right2;
	}

	/**
	 * Левая часть.
	 */
	public Expr getLeft() {
		return left;
	}

	/**
	 * Часть от...
	 */
	public Expr getRight1() {
		return right1;
	}

	/**
	 * Часть до...
	 */
	public Expr getRight2() {
		return right2;
	}

	@Override
	public String getCSQL() {
		return String.format("%s BETWEEN %s AND %s", left.getCSQL(),
				right1.getCSQL(), right2.getCSQL());
	}

	@Override
	public ExprType getType() {
		return ExprType.LOGIC;
	}

	@Override
	public void validateTypes() throws ParseException {
		left.validateTypes();
		right1.validateTypes();
		right2.validateTypes();

		ExprType t = left.getType();
		// Сравнивать можно не все типы.
		if (t == ExprType.DATE || t == ExprType.NUMERIC || t == ExprType.TEXT) {
			// все операнды должны быть однотипны
			right1.assertType(t);
			right2.assertType(t);
		} else {
			throw new ParseException(
					String.format(
							"Wrong expression '%s': type %s cannot be used in ...BETWEEN...AND... expression.",
							getCSQL(), t.toString()));
		}

	}

	@Override
	public void resolveFieldRefs(List<TableRef> tables) throws ParseException {
		left.resolveFieldRefs(tables);
		right1.resolveFieldRefs(tables);
		right2.resolveFieldRefs(tables);
	}

	@Override
	public void accept(ExprVisitor visitor) {
		left.accept(visitor);
		right1.accept(visitor);
		right2.accept(visitor);
		visitor.visitBetween(this);
	}

}

/**
 * IS NULL.
 */
final class IsNull extends Expr {
	private final Expr expr;

	IsNull(View v, Expr expr) throws ParseException {
		super(v);
		if (expr.getType() == ExprType.LOGIC)
			throw new ParseException(
					String.format(
							"Expression '%s' is logical condition and cannot be an argument of IS NULL operator.",
							getCSQL()));
		this.expr = expr;
	}

	/**
	 * Выражение, от которого берётся IS NULL.
	 */
	public Expr getExpr() {
		return expr;
	}

	@Override
	public String getCSQL() {
		return expr.getCSQL() + "IS NULL";
	}

	@Override
	public ExprType getType() {
		return ExprType.LOGIC;
	}

	@Override
	public void validateTypes() throws ParseException {
		expr.validateTypes();
		// Мы уже проверили, что это терм.
	}

	@Override
	public void resolveFieldRefs(List<TableRef> tables) throws ParseException {
		expr.resolveFieldRefs(tables);
	}

	@Override
	public void accept(ExprVisitor visitor) {
		expr.accept(visitor);
		visitor.visitIsNull(this);
	}
}

/**
 * NOT.
 */
final class NotExpr extends Expr {
	private final Expr expr;

	NotExpr(View v, Expr expr) throws ParseException {
		super(v);
		if (expr.getType() != ExprType.LOGIC)
			throw new ParseException(String.format(
					"Expression '%s' is expected to be logical condition.",
					getCSQL()));
		this.expr = expr;
	}

	/**
	 * Выражение, от которого берётся NOT.
	 */
	public Expr getExpr() {
		return expr;
	}

	@Override
	public String getCSQL() {
		return "NOT " + expr.getCSQL();
	}

	@Override
	public ExprType getType() {
		return ExprType.LOGIC;
	}

	@Override
	public void validateTypes() throws ParseException {
		expr.validateTypes();
		// Мы уже проверили, что это логическое условие.
	}

	@Override
	public void resolveFieldRefs(List<TableRef> tables) throws ParseException {
		expr.resolveFieldRefs(tables);
	}

	@Override
	public void accept(ExprVisitor visitor) {
		expr.accept(visitor);
		visitor.visitNotExpr(this);
	}
}

/**
 * AND/OR.
 */
final class BinaryLogicalOp extends Expr {
	public static final int AND = 0;
	public static final int OR = 1;
	private static final String[] OPS = { " AND ", " OR " };

	private final int operator;
	private final List<Expr> operands;

	BinaryLogicalOp(View v, int operator, List<Expr> operands)
			throws ParseException {
		super(v);
		if (operator < 0 || operator >= OPS.length)
			throw new IllegalArgumentException();
		if (operands.isEmpty())
			throw new IllegalArgumentException();
		// все операнды должны быть логическими
		for (Expr e : operands)
			if (e.getType() != ExprType.LOGIC)
				throw new ParseException(String.format(
						"Expression '%s' is expected to be logical condition.",
						e.getCSQL()));
		this.operands = operands;
		this.operator = operator;
	}

	/**
	 * Оператор.
	 */
	public int getOperator() {
		return operator;
	}

	/**
	 * Операнды.
	 */
	public List<Expr> getOperands() {
		return operands;
	}

	@Override
	public String getCSQL() {
		StringBuilder result = new StringBuilder();
		boolean needOp = false;
		for (Expr operand : operands) {
			if (needOp)
				result.append(OPS[operator]);
			result.append(operand.getCSQL());
			needOp = true;
		}
		return result.toString();
	}

	@Override
	public ExprType getType() {
		return ExprType.LOGIC;
	}

	@Override
	public void validateTypes() throws ParseException {
		for (Expr e : operands)
			e.validateTypes();
	}

	@Override
	public void resolveFieldRefs(List<TableRef> tables) throws ParseException {
		for (Expr e : operands)
			e.resolveFieldRefs(tables);
	}

	@Override
	public void accept(ExprVisitor visitor) {
		for (Expr e : operands)
			e.accept(visitor);
		visitor.visitBinaryLogicalOp(this);
	}
}

/**
 * +, -, *, /.
 */
final class BinaryTermOp extends Expr {
	public static final int PLUS = 0;
	public static final int MINUS = 1;
	public static final int TIMES = 2;
	public static final int OVER = 3;
	public static final int CONCAT = 4;
	private static final String[] OPS = { " + ", " - ", " * ", " / ", " || " };

	private final int operator;
	private final List<Expr> operands;

	BinaryTermOp(View v, int operator, List<Expr> operands) {
		super(v);
		if (operator < 0 || operator >= OPS.length)
			throw new IllegalArgumentException();
		if (operands.isEmpty())
			throw new IllegalArgumentException();

		this.operands = operands;
		this.operator = operator;
	}

	/**
	 * Оператор.
	 */
	public int getOperator() {
		return operator;
	}

	/**
	 * Операнды.
	 */
	public List<Expr> getOperands() {
		return operands;
	}

	@Override
	public String getCSQL() {
		StringBuilder result = new StringBuilder();
		boolean needOp = false;
		for (Expr operand : operands) {
			if (needOp)
				result.append(OPS[operator]);
			result.append(operand.getCSQL());
			needOp = true;
		}
		return result.toString();
	}

	@Override
	public ExprType getType() {
		return operator == CONCAT ? ExprType.TEXT : ExprType.NUMERIC;
	}

	@Override
	public void validateTypes() throws ParseException {
		for (Expr e : operands)
			e.validateTypes();

		// для CONCAT все операнды должны быть TEXT, для остальных -- NUMERIC
		ExprType t = operator == CONCAT ? ExprType.TEXT : ExprType.NUMERIC;
		for (Expr e : operands)
			e.assertType(t);

	}

	@Override
	public void resolveFieldRefs(List<TableRef> tables) throws ParseException {
		for (Expr e : operands)
			e.resolveFieldRefs(tables);
	}

	@Override
	public void accept(ExprVisitor visitor) {
		for (Expr e : operands)
			e.accept(visitor);
		visitor.visitBinaryTermOp(this);
	}
}

/**
 * Унарный минус.
 */
final class UnaryMinus extends Expr {
	private final Expr arg;

	public UnaryMinus(View v, Expr arg) {
		super(v);
		this.arg = arg;
	}

	public Expr getExpr() {
		return arg;
	}

	@Override
	public String getCSQL() {
		return "-" + arg.getCSQL();
	}

	@Override
	public ExprType getType() {
		return ExprType.NUMERIC;
	}

	@Override
	public void validateTypes() throws ParseException {
		arg.validateTypes();
		// операнд должен быть NUMERIC
		arg.assertType(ExprType.NUMERIC);
	}

	@Override
	public void resolveFieldRefs(List<TableRef> tables) throws ParseException {
		arg.resolveFieldRefs(tables);
	}

	@Override
	public void accept(ExprVisitor visitor) {
		arg.accept(visitor);
		visitor.visitUnaryMinus(this);
	}
}

/**
 * Числовой нумерал.
 */
final class NumericLiteral extends Expr {
	private final String lexValue;

	NumericLiteral(View v, String lexValue) {
		super(v);
		this.lexValue = lexValue;
	}

	/**
	 * Возвращает лексическое значение.
	 */
	public String getLexValue() {
		return lexValue;
	}

	@Override
	public String getCSQL() {
		return lexValue;
	}

	@Override
	public ExprType getType() {
		return ExprType.NUMERIC;
	}

	@Override
	public void validateTypes() throws ParseException {
		// do nothing, this is literal!
	}

	@Override
	public void resolveFieldRefs(List<TableRef> tables) throws ParseException {
		// do nothing, this is literal!
	}

	@Override
	public void accept(ExprVisitor visitor) {
		visitor.visitNumericLiteral(this);
	}
}

/**
 * Текстовый литерал.
 */
final class TextLiteral extends Expr {
	private final String lexValue;

	TextLiteral(View v, String lexValue) {
		super(v);
		this.lexValue = lexValue;
	}

	/**
	 * Возвращает лексическое значение.
	 */
	public String getLexValue() {
		return lexValue;
	}

	@Override
	public String getCSQL() {
		return lexValue;
	}

	@Override
	public ExprType getType() {
		return ExprType.TEXT;
	}

	@Override
	public void validateTypes() throws ParseException {
		// do nothing, this is literal!
	}

	@Override
	public void resolveFieldRefs(List<TableRef> tables) throws ParseException {
		// do nothing, this is literal!
	}

	@Override
	public void accept(ExprVisitor visitor) {
		visitor.visitTextLiteral(this);
	}

}

/**
 * Ссылка на колонку таблицы.
 */
final class FieldRef extends Expr {
	private final String grainName;
	private final String tableNameOrAlias;
	private final String columnName;
	private Column column = null;

	public FieldRef(View v, String grainName, String tableNameOrAlias,
			String columnName) throws ParseException {
		super(v);
		if (columnName == null)
			throw new IllegalArgumentException();
		if (grainName != null && tableNameOrAlias == null)
			throw new IllegalArgumentException();
		this.grainName = grainName;
		this.tableNameOrAlias = tableNameOrAlias;
		this.columnName = columnName;

	}

	/**
	 * Имя гранулы.
	 */
	public String getGrainName() {
		return grainName;
	}

	/**
	 * Имя или алиас таблицы.
	 */
	public String getTableNameOrAlias() {
		return tableNameOrAlias;
	}

	/**
	 * Имя колонки.
	 */
	public String getColumnName() {
		return columnName;
	}

	@Override
	public String getCSQL() {
		StringBuilder result = new StringBuilder();
		if (grainName != null) {
			result.append(grainName);
			result.append(".");
		}
		if (tableNameOrAlias != null) {
			result.append(tableNameOrAlias);
			result.append(".");
		}
		result.append(columnName);
		return result.toString();
	}

	@Override
	public ExprType getType() {
		if (column != null) {
			if (column instanceof IntegerColumn
					|| column instanceof FloatingColumn)
				return ExprType.NUMERIC;
			if (column instanceof StringColumn)
				return ExprType.TEXT;
			if (column instanceof BooleanColumn)
				return ExprType.BIT;
			if (column instanceof DateTimeColumn)
				return ExprType.DATE;
			if (column instanceof BinaryColumn)
				return ExprType.BLOB;
			// This should not happen unless we introduced new types in Celesta
			throw new IllegalStateException();
		}
		return ExprType.UNDEFINED;
	}

	/**
	 * Возвращает столбец, на который указывает ссылка.
	 */
	public Column getColumn() {
		return column;
	}

	/**
	 * Устанавливает столбец ссылки.
	 */
	public void setColumn(Column column) {
		this.column = column;
	}

	@Override
	public void validateTypes() throws ParseException {
		// do nothing, this is field reference!
	}

	@Override
	public void resolveFieldRefs(List<TableRef> tables) throws ParseException {
		if (column != null)
			return;
		int foundCounter = 0;
		for (TableRef tRef : tables)
			if (grainName == null) {
				if (tableNameOrAlias != null
						&& tableNameOrAlias.equals(tRef.getAlias())) {
					column = tRef.getTable().getColumn(columnName);
					foundCounter++;
				} else if (tableNameOrAlias == null
						&& tRef.getTable().getColumns().containsKey(columnName)) {
					column = tRef.getTable().getColumn(columnName);
					foundCounter++;
				}
			} else {
				if (grainName.equals(tRef.getTable().getGrain().getName())
						&& tableNameOrAlias.equals(tRef.getTable().getName())) {
					column = tRef.getTable().getColumn(columnName);
					foundCounter++;
				}
			}
		if (foundCounter == 0)
			throw new ParseException(String.format(
					"Cannot resolve field reference '%s'", getCSQL()));
		if (foundCounter > 1)
			throw new ParseException(String.format(
					"Ambiguous field reference '%s'", getCSQL()));
	}

	@Override
	public void accept(ExprVisitor visitor) {
		visitor.visitFieldRef(this);
	}
}