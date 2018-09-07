package ru.curs.celesta.score;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.curs.celesta.exception.CelestaParseException;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FkTest {

    private static final String GRAIN_NAME = "grain";
    private static final String INT_COLUMN_NAME = "intcol";
    private static final String TABLE_2_ID_COLUMN_NAME = "idb";

    private Grain grain;
    private Table t1;
    private Table t2;

    @BeforeEach
    void setUp() throws Exception {
        this.grain = new Grain(new CelestaSqlTestScore(), GRAIN_NAME);
        GrainPart gp = new GrainPart(this.grain, true, null);
        this.t1 = new Table(gp, "t1");
        Column cc = new IntegerColumn(t1, "ida");
        cc.setNullableAndDefault(false, "IDENTITY");

        t1.addPK("ida");
        t1.finalizePK();
        new IntegerColumn(t1, INT_COLUMN_NAME);
        new DateTimeColumn(t1, "datecol");

        this.t2 = new Table(gp, "t2");
        cc = new IntegerColumn(t2, "idb");
        cc.setNullableAndDefault(false, "IDENTITY");
        t2.addPK("idb");
        t2.finalizePK();

        new IntegerColumn(t2, INT_COLUMN_NAME);
        new DateTimeColumn(t2, "datecol");
        StringColumn c = new StringColumn(t2, "scol2");
        c.setLength("2");

        c = new StringColumn(t2, "scol5");
        c.setLength("5");
    }


    @Test
    @DisplayName("References resolving successfully when their size is zero")
    void testWithoutFk() {
        assertAll(
                () -> assertDoesNotThrow(t1::resolveReferences),
                () -> assertDoesNotThrow(t2::resolveReferences),
                () -> assertTrue(t1.getForeignKeys().isEmpty()),
                () -> assertTrue(t1.getForeignKeys().isEmpty())
        );
    }

    @Test
    @DisplayName("Fails on adding of unknown column")
    void testFailsOnUnknownColumn() {
        ForeignKey fk = new ForeignKey(t1);
        assertThrows(ParseException.class, () -> fk.addColumn("abracadabra"));
    }

    @Test
    @DisplayName("Fails on adding of duplicated column")
    void testFailsOnDuplicatedColumn() throws Exception {
        final ForeignKey fk = new ForeignKey(t1);
        fk.addColumn(INT_COLUMN_NAME);
        assertThrows(ParseException.class, () -> fk.addColumn(INT_COLUMN_NAME));
    }

    @ValueSource(strings = {"", GRAIN_NAME})
    @ParameterizedTest(name = "{index} ==> grainName=''{0}''")
    @DisplayName("Setting of referencedTable adds FK to parent table, but not resolves the referenced table")
    void testSettingOfReferencedTable(String grainName) throws Exception {
        final ForeignKey fk = new ForeignKey(t1);
        fk.addColumn(INT_COLUMN_NAME);
        // Setting of referencedTable adds FK to parent table
        fk.setReferencedTable(grainName, t2.getName());

        final Set<ForeignKey> foreignKeys = t1.getForeignKeys();

        assertAll(
                () -> assertEquals(1, foreignKeys.size()),
                () -> assertEquals(1, foreignKeys.stream().findFirst().get().getColumns().size()),
                () -> assertTrue(foreignKeys.stream().findFirst().get().getColumns().containsKey(INT_COLUMN_NAME)),
                () -> assertSame(
                        t1.getColumn(INT_COLUMN_NAME),
                        foreignKeys.stream().findFirst().get().getColumns().get(INT_COLUMN_NAME)
                ),
                () -> assertNull(fk.getReferencedTable())
        );
    }

    @Test
    @DisplayName("Reference resolving fails without referenced column")
    void testReferenceResolvingFailsWithoutReferencedColumn() throws Exception {
        final ForeignKey fk = new ForeignKey(t1);
        fk.addColumn(INT_COLUMN_NAME);
        // Setting of referencedTable adds FK to parent table
        fk.setReferencedTable(GRAIN_NAME, t2.getName());
        assertThrows(CelestaParseException.class, t1::resolveReferences);
    }

    @Test
    @DisplayName("Reference resolving fails with not existing referenced column")
    void testReferenceResolvingFailsWithNotExistingReferencedColumn() throws Exception {
        final ForeignKey fk = new ForeignKey(t1);
        fk.addColumn(INT_COLUMN_NAME);
        // Setting of referencedTable adds FK to parent table
        fk.setReferencedTable(GRAIN_NAME, t2.getName());
        fk.addReferencedColumn("blahblah");
        assertThrows(CelestaParseException.class, t1::resolveReferences);
    }

    @Test
    @DisplayName("Reference resolving fails with existing referenced not PK column")
    void testReferenceResolvingFailsWithExistingNotPkReferencedColumn() throws Exception {
        final ForeignKey fk = new ForeignKey(t1);
        fk.addColumn(INT_COLUMN_NAME);
        // Setting of referencedTable adds FK to parent table
        fk.setReferencedTable(GRAIN_NAME, t2.getName());
        fk.addReferencedColumn(INT_COLUMN_NAME);
        assertThrows(CelestaParseException.class, t1::resolveReferences);
    }

    @Test
    @DisplayName("Reference resolving is success with existing referenced PK column")
    void testReferenceResolvingSuccessWithExistingPkColumn() throws Exception {
        final ForeignKey fk = new ForeignKey(t1);
        fk.addColumn(INT_COLUMN_NAME);
        // Setting of referencedTable adds FK to parent table
        fk.setReferencedTable(GRAIN_NAME, t2.getName());
        fk.addReferencedColumn(TABLE_2_ID_COLUMN_NAME);

        final Set<ForeignKey> foreignKeys = t1.getForeignKeys();

        assertAll(
                () -> assertDoesNotThrow(t1::resolveReferences),
                () -> assertEquals(Collections.singleton(fk), foreignKeys),
                () -> assertEquals(1, fk.getColumns().size()),
                () -> assertTrue(fk.getColumns().containsKey(INT_COLUMN_NAME)),
                () -> assertSame(
                        t1.getColumn(INT_COLUMN_NAME),
                        fk.getColumns().get(INT_COLUMN_NAME)
                ),
                () -> assertSame(t2, fk.getReferencedTable())
        );
    }
}
