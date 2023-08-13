
package com.car.control.browser;

import android.graphics.Canvas;

public interface ISurfaceAdapter {

	int getItemWidth();

	int getItemHeight();

	int getRows();

	int getColumns();

	int getItemCount();

	void drawItem(int itemIndex, Canvas canvas, float x, float y, float rotate, float scale);

	void setSelectedItem(int item);

	int getSelectedItem();

	Object getItem(int index);
}
