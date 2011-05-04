/*
 * Copyright (C) 2010-2011 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.android.fbreader.network;

import java.util.*;

import android.app.Activity;
import android.os.Message;
import android.os.Handler;

import org.geometerplus.zlibrary.core.network.ZLNetworkException;

import org.geometerplus.fbreader.network.INetworkLink;
import org.geometerplus.fbreader.network.NetworkOperationData;
import org.geometerplus.fbreader.network.NetworkItem;

abstract class ItemsLoadingRunnable implements Runnable {
	private final Activity myActivity;

	private final LinkedList<NetworkItem> myItems = new LinkedList<NetworkItem>();
	private final HashMap<INetworkLink, LinkedList<NetworkItem>> myUncommitedItems = new HashMap<INetworkLink, LinkedList<NetworkItem>>();
	private final Object myItemsMonitor = new Object();

	private volatile boolean myFinishProcessed;
	private final Object myFinishMonitor = new Object();

	private final long myUpdateInterval; // in milliseconds

	private boolean myInterruptRequested;
	private boolean myInterruptConfirmed;
	private Object myInterruptLock = new Object();

	private boolean myFinished;
	private Handler myFinishedHandler;
	private Object myFinishedLock = new Object();

	ItemsLoadingRunnable(Activity activity) {
		this(activity, 1000);
	}

	ItemsLoadingRunnable(Activity activity, long updateIntervalMillis) {
		myActivity = activity;
		myUpdateInterval = updateIntervalMillis;
	}


	public void interruptLoading() {
		synchronized (myInterruptLock) {
			myInterruptRequested = true;
		}
	}

	private boolean confirmInterruptLoading() {
		synchronized (myInterruptLock) {
			if (myInterruptRequested) {
				myInterruptConfirmed = true;
			}
			return myInterruptConfirmed;
		}
	}

	public boolean tryResumeLoading() {
		synchronized (myInterruptLock) {
			if (!myInterruptConfirmed) {
				myInterruptRequested = false;
			}
			return !myInterruptRequested;
		}
	}

	private boolean isLoadingInterrupted() {
		synchronized (myInterruptLock) {
			return myInterruptConfirmed;
		}
	}


	public final void run() {
		try {
			doBefore();
		} catch (ZLNetworkException e) {
			sendFinish(e.getMessage(), false);
			return;
		}
		String error = null;
		try {
			doLoading(new NetworkOperationData.OnNewItemListener() {
				private long myUpdateTime;
				private int myItemsNumber;
				public void onNewItem(INetworkLink link, NetworkItem item) {
					addItem(link, item);
					++myItemsNumber;
					final long now = System.currentTimeMillis();
					if (now > myUpdateTime) {
						updateItemsOnUiThread();
						myUpdateTime = now + myUpdateInterval;
					}
				}
				public boolean confirmInterrupt() {
					return confirmInterruptLoading();
				}
				public void commitItems(INetworkLink link) {
					commitItems(link);
				}
			});
		} catch (ZLNetworkException e) {
			error = e.getMessage();
		}

		updateItemsOnUiThread();
		ensureItemsProcessed();
		sendFinish(error, isLoadingInterrupted());
		ensureFinishProcessed();
	}

	void runFinishHandler() {
		synchronized (myFinishedLock) {
			if (myFinishedHandler != null) {
				myFinishedHandler.sendEmptyMessage(0);
			}
			myFinished = true;
		}
	}


	public void runOnFinish(final Runnable runnable) {
		if (myFinishedHandler != null) {
			return;
		}
		synchronized (myFinishedLock) {
			if (myFinished) {
				runnable.run();
			} else {
				myFinishedHandler = new Handler() {
					public void handleMessage(Message message) {
						runnable.run();
					}
				};
			}
		}
	}

	private final void updateItemsOnUiThread() {
		myActivity.runOnUiThread(new Runnable() {
			public void run() {
				doUpdateItems();
			}
		});
	}

	private final void doUpdateItems() {
		synchronized (myItemsMonitor) {
			updateItems(myItems);
			myItems.clear();
			myItemsMonitor.notifyAll(); // wake up process, that waits for finish condition (see ensureFinish() method)
		}
	}

	public final void addItem(INetworkLink link, NetworkItem item) {
		synchronized (myItemsMonitor) {
			myItems.add(item);
			LinkedList<NetworkItem> uncommited = myUncommitedItems.get(link);
			if (uncommited == null) {
				uncommited = new LinkedList<NetworkItem>();
				myUncommitedItems.put(link, uncommited);
			}
			uncommited.add(item);
		}
	}

	public final void commitItems(INetworkLink link) {
		synchronized (myItemsMonitor) {
			LinkedList<NetworkItem> uncommited = myUncommitedItems.get(link);
			if (uncommited != null) {
				uncommited.clear();
			}
		}
	}

	public final void ensureItemsProcessed() {
		synchronized (myItemsMonitor) {
			while (myItems.size() > 0) {
				try {
					myItemsMonitor.wait();
				} catch (InterruptedException e) {
				}
			}
		}
	}

	public final void ensureFinishProcessed() {
		synchronized (myFinishMonitor) {
			while (!myFinishProcessed) {
				try {
					myFinishMonitor.wait();
				} catch (InterruptedException e) {
				}
			}
		}
	}

	private final void doProcessFinish(String errorMessage, boolean interrupted) {
		HashSet<NetworkItem> uncommitedItems = new HashSet<NetworkItem>();
		synchronized (myUncommitedItems) {
			for (LinkedList<NetworkItem> items: myUncommitedItems.values()) {
				uncommitedItems.addAll(items);
			}
		}
		synchronized (myFinishMonitor) {
			onFinish(errorMessage, interrupted, uncommitedItems);
			myFinishProcessed = true;
			// wake up process, that waits for finish condition (see ensureFinish() method)
			myFinishMonitor.notifyAll();
		}
	}

	public final void sendFinish(final String errorMessage, final boolean interrupted) {
		myActivity.runOnUiThread(new Runnable() {
			public void run() {
				doProcessFinish(errorMessage, interrupted);
			}
		});
	}

	protected abstract void onFinish(String errorMessage, boolean interrupted, Set<NetworkItem> uncommitedItems);

	protected abstract void updateItems(List<NetworkItem> items);

	public abstract void doBefore() throws ZLNetworkException;
	public abstract void doLoading(NetworkOperationData.OnNewItemListener doWithListener) throws ZLNetworkException;

	public abstract String getResourceKey();
}
