// $Id$

package net.sf.persist.tests.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import net.sf.persist.*;
import net.sf.persist.tests.framework.ConnectionHelper;
import net.sf.persist.tests.framework.DynamicBean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TestSimple {
    private static final Logger LOG = LoggerFactory.getLogger(TestSimple.class);

	protected Connection connection = null;
	protected Persist persist = null;

	/**
	 * Must be implemented in subclasses to return the path for the database properties file
	 */
	public abstract String getProperties();

	@Before
	public void setUp() throws SQLException {
		String properties = getProperties();
		this.connection = ConnectionHelper.getConnection(properties);
		this.connection.createStatement().execute("delete from simple");
		this.persist = new Persist(connection);
	}

	@After
	public void tearDown() throws SQLException {
		this.connection.close();
	}

	public static Simple buildSimple() {
		Simple simple = new Simple();
		simple.setIntCol(DynamicBean.randomInt(0, Integer.MAX_VALUE / 2));
		simple.setStringCol(DynamicBean.randomString(255));
		return simple;
	}
	
	@Test
	public void testNoTable() {
		Simple simple = buildSimple();
		persist.insert(simple);
		
		SimpleNoTable simpleNoTable= persist.read(SimpleNoTable.class, "select * from simple");
		assertEquals(simple.getIntCol(), simpleNoTable.getIntCol());
		assertEquals(simple.getStringCol(), simpleNoTable.getStringCol());
	}

	@Test
	public void testCacheName() {
		Persist persist1 = persist;
		Persist persist2 = new Persist("my cache", connection);

		// 2 objects coming from mappings stored in different caches should be equal
		Simple simple1 = buildSimple();
		persist1.insert(simple1);
		Simple simple2 = (Simple) persist2.readList(Simple.class).get(0);

		// will not assert ids are equal, since isAutoUpdateGeneratedKeys()==false
		assertEquals(simple1, simple2);

		// 2 mappings coming from different caches should be different
		TableMapping m1 = (TableMapping) persist1.getMapping(Simple.class);
		TableMapping m2 = (TableMapping) persist2.getMapping(Simple.class);
		assertTrue(m1 != m2);

		// make sure that null will return the default cache
		Persist persist1a = new Persist(null, connection);
		TableMapping m1a = (TableMapping) persist1a.getMapping(Simple.class);
		assertEquals(true, m1 == m1a);
	}

	@Test
	public void testExecuteUpdate() {

		// some data to insert
		int intCol = DynamicBean.randomInt(0, Integer.MAX_VALUE / 2);
		String stringCol = DynamicBean.randomString(255);

		// insert and check count of rows returned
		int n = persist.executeUpdate("insert into simple (int_col, string_col) values(?,?)", intCol, stringCol);
		assertEquals(1, n);

		// read object and compare with inserted data
		Simple simpleRead = persist.read(Simple.class, "select * from simple where int_col=? and string_col=?", 
				intCol,stringCol);
		
		assertNotNull(simpleRead);
		assertEquals(intCol, simpleRead.getIntCol());
		assertEquals(stringCol, simpleRead.getStringCol());

		// delete object and check it was removed
		persist.delete(simpleRead);
		simpleRead = persist.read(Simple.class, "select * from simple where int_col=? and string_col=?", 
				intCol, stringCol);
		assertNull(simpleRead);
	}

	@Test
	public void testExecuteUpdateAutoGeneratedKeys() {

		TableMapping mapping = (TableMapping) persist.getMapping(Simple.class);
		if (mapping.supportsGetGeneratedKeys()) {

			// some data to insert
			int intCol = DynamicBean.randomInt(0, Integer.MAX_VALUE / 2);
			String stringCol = DynamicBean.randomString(255);

			// insert with explicit auto generated keys and check result object data
			String[] autoGeneratedKeys = new String[] { "id" };
			Result result = persist.executeUpdate(Simple.class, "insert into simple (int_col,string_col) values(?,?)",
					autoGeneratedKeys, intCol, stringCol);
			assertEquals(1, result.getGeneratedKeys().size());
			assertEquals(1, result.getRowsModified());

			// read object and compare with inserted data
			Simple simpleRead = persist.read(Simple.class, "select * from simple where int_col=? and string_col=?",
					intCol, stringCol);
			assertNotNull(simpleRead);
			assertEquals(intCol, simpleRead.getIntCol());
			assertEquals(stringCol, simpleRead.getStringCol());

			// delete object and check it was removed
			persist.delete(simpleRead);
			simpleRead = persist.read(Simple.class, "select * from simple where int_col=? and string_col=?", 
					intCol, stringCol);
			assertNull(simpleRead);
		} else {
			LOG.debug("This database does not support retrieval of auto generated keys");
		}

	}

	@Test
	public void testSetAutoGeneratedKeys() {

		TableMapping mapping = (TableMapping) persist.getMapping(Simple.class);
		if (mapping.supportsGetGeneratedKeys()) {

			// insert object with setUpdateAutoGeneratedKeys option
			Simple simpleInsert = TestSimple.buildSimple();
			simpleInsert.setId(0);
			persist.setUpdateAutoGeneratedKeys(true);
			persist.insert(simpleInsert);
			assertTrue(0 != simpleInsert.getId());

			int id = persist.read(int.class, "select id from simple");
			assertEquals(id, simpleInsert.getId());

			// read object using primary key (auto generated)
			Simple simpleRead = persist.read(Simple.class, "select * from simple where id=?", simpleInsert.getId());
			assertEquals(simpleInsert, simpleRead);

			// delete object by primary key and check it was removed
			persist.delete(simpleRead);
			simpleRead = persist.readByPrimaryKey(Simple.class, simpleRead.getId());
			assertNull(simpleRead);
			
		} else {
			LOG.debug("This database does not support retrieval of auto generated keys");
		}
	}

	@Test
	public void testReturnNativeTypes() {

		Simple simple = buildSimple();
		persist.insert(simple);
		int intCol = persist.read(int.class, "select int_col from simple");
		String stringCol = persist.read(String.class, "select string_col from simple");

		assertEquals(simple.getIntCol(), intCol);
		assertEquals(simple.getStringCol(), stringCol);
	}

	@Test
	public void testBatch() {

		Simple simple1 = buildSimple();
		Simple simple2 = buildSimple();
		Simple simple3 = buildSimple();
		persist.insertBatch(simple1, simple2, simple3);

		List<Simple> list = persist.readList(Simple.class);
		List<Simple> s = new ArrayList<Simple>();
		s.add(simple1);
		s.add(simple2);
		s.add(simple3);
		assertTrue(s.containsAll(list));

		Simple s1 = list.get(0);
		Simple s2 = list.get(1);
		Simple s3 = list.get(2);

		s1.setIntCol(simple1.getIntCol() + 1);
		s1.setStringCol(simple1.getStringCol().toUpperCase());
		s2.setIntCol(simple2.getIntCol() + 1);
		s2.setStringCol(simple2.getStringCol().toUpperCase());
		s3.setIntCol(simple3.getIntCol() + 1);
		s3.setStringCol(simple3.getStringCol().toUpperCase());

		s = new ArrayList<Simple>();
		s.add(s1);
		s.add(s2);
		s.add(s3);

		persist.updateBatch(s1, s2, s3);
		list = persist.readList(Simple.class);
		assertTrue(s.containsAll(list));

		persist.deleteBatch(s1, s2, s3);
		list = persist.readList(Simple.class);
		assertEquals(0, list.size());
	}

	@Test
	public void testObject() {

		Simple simpleInsert = buildSimple();
		persist.insert(simpleInsert);

		int id = persist.read(int.class, "select id from simple");

		Simple simpleUpdate = persist.readByPrimaryKey(Simple.class, id);
		assertEquals(simpleInsert, simpleUpdate);

		simpleUpdate.setIntCol(DynamicBean.randomInt(0, Integer.MAX_VALUE / 2));
		simpleUpdate.setStringCol(DynamicBean.randomString(255));
		persist.update(simpleUpdate);

		Simple simpleRead = persist.readByPrimaryKey(Simple.class, simpleUpdate.getId());
		assertEquals(simpleUpdate, simpleRead);

		persist.delete(simpleRead);

		Simple simpleDeleted = persist.readByPrimaryKey(Simple.class, simpleRead.getId());
		assertNull(simpleDeleted);
	}

	@Test
	public void testObjectList() {

		Simple simple1 = buildSimple();
		Simple simple2 = buildSimple();
		Simple simple3 = buildSimple();
		persist.insert(simple1);
		persist.insert(simple2);
		persist.insert(simple3);

		List<Simple> list = persist.readList(Simple.class);
		List<Simple> s = new ArrayList<Simple>();
		s.add(simple1);
		s.add(simple2);
		s.add(simple3);
		assertTrue(s.containsAll(list));

		persist.delete(list.get(0));
		persist.delete(list.get(1));
		persist.delete(list.get(2));
		List<Simple> listDeleted = persist.readList(Simple.class);
		assertTrue(0 == listDeleted.size());
	}

	@Test
	public void testObjectIterator() {

		Simple simple1 = buildSimple();
		Simple simple2 = buildSimple();
		Simple simple3 = buildSimple();
		persist.insertBatch(simple1, simple2, simple3);
		List<Simple> s = new ArrayList<Simple>();
		s.add(simple1);
		s.add(simple2);
		s.add(simple3);

		ResultSetIterator<Simple> i = persist.readIterator(Simple.class);
		List<Simple> si = new ArrayList<Simple>();
		while (i.hasNext()) {
			si.add(i.next());
		}
        i.close();

		assertTrue(s.containsAll(si));
	}

	@Test
	public void testMap() {

		Simple simple = buildSimple();
		persist.insert(simple);

		int id = persist.read(int.class, "select id from simple");

		Map<String, Object> simpleMap1 = persist.readMap("select * from simple where id=?", id);
		assertEquals(id, simpleMap1.get("id"));
		assertEquals(simple.getIntCol(), simpleMap1.get("int_col"));
		assertEquals(simple.getStringCol(), simpleMap1.get("string_col"));

		persist.delete(simple);
	}

	@Test
	public void testMapList() {

		Simple simple1 = buildSimple();
		Simple simple2 = buildSimple();
		Simple simple3 = buildSimple();

		persist.insertBatch(simple1, simple2, simple3);

		// tests using setAutoUpdateGeneratedKeys do not belong here
		List<Integer> ids = persist.readList(int.class, "select id from simple order by id");
		simple1.setId(ids.get(0));
		simple2.setId(ids.get(1));
		simple3.setId(ids.get(2));

		List<Map<String, Object>> simpleList = persist.readMapList("select * from simple where id in (?,?,?)", simple1
				.getId(), simple2.getId(), simple3.getId());
		assertEquals(simple1.getId(), simpleList.get(0).get("id"));
		assertEquals(simple1.getIntCol(), simpleList.get(0).get("int_col"));
		assertEquals(simple1.getStringCol(), simpleList.get(0).get("string_col"));

		assertEquals(simple2.getId(), simpleList.get(1).get("id"));
		assertEquals(simple2.getIntCol(), simpleList.get(1).get("int_col"));
		assertEquals(simple2.getStringCol(), simpleList.get(1).get("string_col"));

		assertEquals(simple3.getId(), simpleList.get(2).get("id"));
		assertEquals(simple3.getIntCol(), simpleList.get(2).get("int_col"));
		assertEquals(simple3.getStringCol(), simpleList.get(2).get("string_col"));

		persist.delete(simple1);
		persist.delete(simple2);
		persist.delete(simple3);
	}

	@Test
	public void testMapIterator() {

		Simple simple1 = buildSimple();
		Simple simple2 = buildSimple();
		Simple simple3 = buildSimple();
		persist.setUpdateAutoGeneratedKeys(false);
		persist.insertBatch(simple1, simple2, simple3);
		Simple[] simpleArray = new Simple[] { simple1, simple2, simple3 };

		Set<Simple> simpleSet = new HashSet<Simple>();

		ResultSetIterator<Map<String, Object>> i = persist.readMapIterator("select * from simple");
		while (i.hasNext()) {
			Map<String, Object> m = i.next();
			for (int n = 0; n < 3; n++) {
				if (simpleArray[n].getIntCol() == ((Number) m.get("int_col")).intValue()
						&& simpleArray[n].getStringCol().equals((String) m.get("string_col"))) {
					simpleSet.add(simpleArray[n]);
				}
			}
		}
        i.close();

		assertEquals(3, simpleSet.size());
	}

	@Test
	public void testMapping() {
		Simple simple = buildSimple();
		persist.insert(simple);

		// tests using setAutoUpdateGeneratedKeys do not belong here
		int id = persist.read(int.class, "select id from simple");
		simple.setId(id);

		// Simple01 specifies an invalid column name
		try {
			persist.readByPrimaryKey(Simple01.class, simple.getId());
			fail("Object with invalid column name did not trigger exception");
		} catch (PersistException e) {
			assertEquals(e.getMessage(),
					"Field [intCol] from class [net.sf.persist.tests.common.Simple01] specifies column [hello_world] on table [simple] that does not exist in the database");
		}

		// Simple02 specifies an invalid table
		try {
			persist.readByPrimaryKey(Simple02.class, simple.getId());
			fail("Object with invalid table name did not trigger exception");
		} catch (PersistException e) {
			assertEquals(e.getMessage(),
					"Class [net.sf.persist.tests.common.Simple02] specifies table [hello_world] that does not exist in the database");
		}

		// Simple03 lacks a field
		try {
			persist.readByPrimaryKey(Simple03.class, simple.getId());
			fail("Object lacking field did not trigger exception");
		} catch (PersistException e) {
			assertEquals(e.getMessage(),
					"Column [int_col] from result set does not have a mapping to a field in [net.sf.persist.tests.common.Simple03]");
		}

		// Simple04 has incompatible getter and setter
		try {
			persist.readByPrimaryKey(Simple04.class, simple.getId());
			fail("Object with incompatible getter and setter did not trigger exception");
		} catch (PersistException e) {
			assertEquals(e.getMessage(),
					"Getter [public long net.sf.persist.tests.common.Simple04.getIntCol()] and setter [public void net.sf.persist.tests.common.Simple04.setIntCol(boolean)] have incompatible types");
		}

		// Simple05 doesn't specify a table name and guessed names won't work
		try {
			persist.readByPrimaryKey(Simple05.class, simple.getId());
			fail("Object with invalid table name did not trigger exception");
		} catch (PersistException e) {
			assertEquals(
					e.getMessage(),
					"Class [net.sf.persist.tests.common.Simple05] does not specify a table name through a Table annotation and no guessed table names [simple05, simple05s] exist in the database");
		}

		// Simple06 has different annotations for getter and setter
		try {
			persist.readByPrimaryKey(Simple06.class, simple.getId());
			fail("Object with different annotations for getter and setter did not trigger exception");
		} catch (PersistException e) {
			assertTrue(e.getMessage()
					.startsWith("Annotations for getter [public long net.sf.persist.tests.common.Simple06.getIntCol()] and setter [public void net.sf.persist.tests.common.Simple06.setIntCol(long)] have different annotations"));
		}

		// Simple07 doesn't have a getter and setter for string_col
		try {
			persist.readByPrimaryKey(Simple07.class, simple.getId());
			fail("Object without getter and setter did not trigger exception");
		} catch (PersistException e) {
			assertEquals(e.getMessage(),
					"Field [foo] from class [net.sf.persist.tests.common.Simple07] does not specify a column name through a Column annotation and no guessed column names [foo, foos] exist in the database. If this field is not supposed to be associated with the database, please annotate it with @NoColumn");
		}

		// Simple08 has conflicting Column and NoColumn annotations
		try {
			persist.readByPrimaryKey(Simple08.class, simple.getId());
			fail("Object with conflicting annotations did not trigger exception");
		} catch (PersistException e) {
			assertEquals(e.getMessage(),
					"Field [intCol] from class [net.sf.persist.tests.common.Simple08] has conflicting NoColumn and Column annotations");
		}

		// Simple09 has getter which returns void
		try {
			persist.readByPrimaryKey(Simple09.class, simple.getId());
			fail("Object with getter returning void did not trigger exception");
		} catch (PersistException e) {
			assertEquals(e.getMessage(),
					"Getter [public void net.sf.persist.tests.common.Simple09.getStringCol()] must have a return parameter");
		}

		// Simple10 has setter with no parameters
		try {
			persist.readByPrimaryKey(Simple10.class, simple.getId());
			fail("Object with setter having no parameters did not trigger exception");
		} catch (PersistException e) {
			assertEquals(e.getMessage(),
					"Setter [public void net.sf.persist.tests.common.Simple10.setStringCol()] should have a single parameter but has 0");
		}

	}
	
	@Test
	public void TestGuessColumn() {
		
		DefaultNameGuesser guesser = new DefaultNameGuesser();
		
		Set<String> guessed = guesser.guessColumn("name");
		Set<String> expected = toSet(new String[] { "name", "names" });
		assertEquals(expected, guessed);
		
		guessed = guesser.guessColumn("nameC");
		expected = toSet(new String[] { "namec", "name_c", "namecs", "name_cs" });
		assertEquals(expected, guessed);
		
		guessed = guesser.guessColumn("nameCo");
		expected = toSet(new String[] { "nameco", "name_co", "namecos", "name_cos" });
		assertEquals(expected, guessed);

		guessed = guesser.guessColumn("n");
		expected = toSet(new String[] { "n", "ns" });
		assertEquals(expected, guessed);		

		guessed = guesser.guessColumn("nC");
		expected = toSet(new String[] { "nc", "ncs", "n_c", "n_cs" });
		assertEquals(expected, guessed);		
		
		guessed = guesser.guessColumn("nCMP");
		expected = toSet(new String[] { "n_c_m_p", "ncmp", "n_c_m_ps", "ncmps", });
		assertEquals(expected, guessed);		
	}
	
	private static Set<String> toSet(String[] values) {
		Set<String> set = new LinkedHashSet<String>();
        set.addAll(Arrays.asList(values));
		return set;
	}

}
