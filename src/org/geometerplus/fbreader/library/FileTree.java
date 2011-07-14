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

package org.geometerplus.fbreader.library;

import java.util.List;

import org.geometerplus.zlibrary.core.filesystem.ZLFile;
import org.geometerplus.zlibrary.core.image.ZLImage;

import org.geometerplus.fbreader.formats.PluginCollection;
import org.geometerplus.fbreader.tree.FBTree;

public class FileTree extends LibraryTree {
	private final ZLFile myFile;
	private final String myName;
	private final String mySummary;
	private final boolean myIsSelectable;

	public FileTree(LibraryTree parent, ZLFile file, String name, String summary) {
		super(parent);
		myFile = file;
		myName = name;
		mySummary = summary;
		myIsSelectable = false;
	}

	public FileTree(FileTree parent, ZLFile file) {
		super(parent);
		if (file.isArchive() && file.getPath().endsWith(".fb2.zip")) {
			final List<ZLFile> children = file.children();
			if (children.size() == 1) {
				final ZLFile child = children.get(0);
				if (child.getPath().endsWith(".fb2")) {
					myFile = child;
					myName = file.getLongName();
					mySummary = null;
					myIsSelectable = true;
					return;
				}
			} 
		}
		myFile = file;
		myName = null;
		mySummary = null;
		myIsSelectable = true;
	}

	@Override
	public String getName() {
		return myName != null ? myName : myFile.getShortName();
	}

	@Override
	public String getTreeTitle() {
		return myFile.getPath();
	}

	@Override
	protected String getStringId() {
		return myFile.getPath();
	}

	@Override
	public String getSummary() {
		if (mySummary != null) {
			return mySummary;
		}

		final Book book = getBook();
		if (book != null) {
			return book.getTitle();
		}

		return null;
	}

	public boolean isSelectable() {
		return myIsSelectable;
	}

	@Override
	public ZLImage createCover() {
		return Library.getCover(myFile);
	}

	public ZLFile getFile() {
		return myFile;
	}

	@Override
	public Book getBook() {
		return Book.getByFile(myFile);
	}

	@Override
	public Status getOpeningStatus() {
		if (!myFile.isReadable()) {
			return Status.CANNOT_OPEN;
		}
		return Status.ALWAYS_RELOAD_BEFORE_OPENING;
	}

	@Override
	public String getOpeningStatusMessage() {
		return getOpeningStatus() == Status.CANNOT_OPEN ? "permissionDenied" : null;
	}

	@Override
	public void waitForOpening() {
		if (getBook() != null) {
			return;
		}
		clear();
		for (ZLFile file : myFile.children()) {
			if (file.isDirectory() || file.isArchive() ||
				PluginCollection.Instance().getPlugin(file) != null) {
				new FileTree(this, file);
			}
		}
		sortAllChildren();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof FileTree)) {
			return true;
		}
		return myFile.equals(((FileTree)o).myFile);
	}

	@Override
	public int compareTo(FBTree tree) {
		final FileTree fileTree = (FileTree)tree;
		final boolean isDir = myFile.isDirectory();
		if (isDir != fileTree.myFile.isDirectory()) {
			return isDir ? -1 : 1;
		} 
		return getName().compareToIgnoreCase(fileTree.getName());
	}
}
