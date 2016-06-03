package ru.curs.lyra;

import java.util.*;

import ru.curs.celesta.*;
import ru.curs.celesta.dbutils.*;

/**
 * Base Java class for Lyra grid form.
 */
public abstract class BasicGridForm extends BasicLyraForm {

	private final GridDriver gd;

	public BasicGridForm(CallContext context) throws CelestaException {
		super(context);
		gd = new GridDriver(_getCursor(context));
		// TODO Recreate GridDriver each time filtering and sorting changes!!!!
	}

	/**
	 * Returns contents of grid given scrollbar's position.
	 * 
	 * @param position
	 *            New scrollbar's position.
	 * @throws CelestaException
	 *             e. g. insufficient access rights
	 * @throws ParseException
	 *             something wrong
	 */
	public synchronized List<LyraFormData> getRows(int position) throws CelestaException {
		BasicCursor c = rec();
		if (gd.setPosition(position, c)) {
			return returnRows(c);
		} else {
			return Collections.emptyList();
		}
	}

	/**
	 * Positions grid to a certain record.
	 * 
	 * @param pk
	 *            Values of primary key.
	 * 
	 * @throws CelestaException
	 *             e. g. insufficient access rights
	 * @throws ParseException
	 *             something wrong
	 */
	public synchronized List<LyraFormData> setPosition(Object... pk) throws CelestaException {
		BasicCursor bc = rec();
		if (bc instanceof Cursor) {
			Cursor c = (Cursor) bc;
			if (c.meta().getPrimaryKey().size() != pk.length)
				throw new CelestaException(
						"Invalid number of 'setPosition' arguments for '%s': expected %d, provided %d.",
						c.meta().getName(), c.meta().getPrimaryKey().size(), pk.length);
			int i = 0;
			for (String name : c.meta().getPrimaryKey().keySet()) {
				c.setValue(name, pk[i++]);
			}
		} else {
			bc.setValue(bc.meta().getColumns().keySet().iterator().next(), pk[0]);
		}

		if (bc.navigate("=<-")) {
			gd.setPosition(bc);
			return returnRows(bc);
		} else {
			return Collections.emptyList();
		}
	}

	private List<LyraFormData> returnRows(BasicCursor c) throws CelestaException {
		final int h = getGridHeight();
		final String id = _getId();
		final List<LyraFormData> result = new ArrayList<>(h);
		final Map<String, LyraFormField> meta = getFieldsMeta();

		for (int i = 0; i < h; i++) {
			LyraFormData lfd = new LyraFormData(c, meta, id);
			result.add(lfd);
			if (!c.next())
				break;
		}

		return result;
	}

	/**
	 * Sets change notifier to be run when refined grid parameters are ready.
	 * 
	 * @param callback
	 *            A callback to be run.
	 */
	public void setChangeNotifier(Runnable callback) {
		gd.setChangeNotifier(callback);
	}

	/**
	 * Returns change notifier.
	 */
	public Runnable getChangeNotifier() {
		return gd.getChangeNotifier();
	}

	/**
	 * If the grid is scrolled less than for given amount of records, the exact
	 * positioning in cycle will be used instead of interpolation.
	 * 
	 * @param val
	 *            new value.
	 */
	public void setMaxExactScrollValue(int val) {
		gd.setMaxExactScrollValue(val);
	}

	/**
	 * Returns (approximate) total record count.
	 * 
	 * Just after creation of the form this method returns DEFAULT_COUNT value,
	 * but it asynchronously requests total count right after constructor
	 * execution.
	 */
	public int getApproxTotalCount() {
		return gd.getApproxTotalCount();

	}

	/**
	 * Returns scrollbar's knob position for current cursor value.
	 */
	public int getTopVisiblePosition() {
		return gd.getTopVisiblePosition();
	}

	/**
	 * Should return a number of rows in grid.
	 */
	public abstract int getGridHeight();
}