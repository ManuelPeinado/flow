/*
 * Copyright 2013 Square Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.squareup.flow;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/** Describes the history of a {@link Flow} at a specific point in time. For persisting the
 * supplied {@link Parcer} needs to be able to handle all screen types. */
public final class Backstack implements Iterable<Backstack.Entry> {
  private final long highestId;
  private final Deque<Entry> backstack;

  /** Restore a saved backstack from a {@link Parcelable} using the supplied {@link Parcer}. */
  public static Backstack from(Parcelable parcelable, Parcer<Screen> parcer) {
    ParcelableBackstack backstack = (ParcelableBackstack) parcelable;
    return backstack.getBackstack(parcer);
  }

  public static Builder emptyBuilder() {
    return new Builder(-1, Collections.<Entry>emptyList());
  }

  /** Create a backstack that contains a single screen. */
  public static Backstack single(Screen screen) {
    return emptyBuilder().push(screen).build();
  }

  private Backstack(long highestId, Deque<Entry> backstack) {
    this.highestId = highestId;
    this.backstack = backstack;
  }

  @Override public Iterator<Entry> iterator() {
    return new ReadIterator<Entry>(backstack.iterator());
  }

  public Iterator<Entry> reverseIterator() {
    return new ReadIterator<Entry>(backstack.descendingIterator());
  }

  /** Get a {@link Parcelable} of this backstack using the supplied {@link Parcer}. */
  public Parcelable getParcelable(Parcer<Screen> parcer) {
    return new ParcelableBackstack.Memory(this, parcer);
  }

  public int size() {
    return backstack.size();
  }

  public Entry current() {
    return backstack.peek();
  }

  /** Get a builder to modify a copy of this backstack. */
  public Builder buildUpon() {
    return new Builder(highestId, backstack);
  }

  @Override public String toString() {
    return backstack.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Backstack entries = (Backstack) o;

    return highestId == entries.highestId && equals(backstack, entries.backstack);
  }

  @Override
  public int hashCode() {
    int result = (int) (highestId ^ (highestId >>> 32));
    result = 31 * result + hashCode(backstack);
    return result;
  }

  private static <T> boolean equals(Deque<T> a, Deque<T> b) {
    if (a.size() != b.size()) {
      return false;
    }

    Iterator<?> it1 = a.iterator(), it2 = b.iterator();
    while (it1.hasNext()) {
      Object e1 = it1.next(), e2 = it2.next();
      if (!e1.equals(e2)) {
        return false;
      }
    }
    return true;
  }

  private static <T> int hashCode(Iterable<T> iterable) {
    int result = 1;
    for (T object : iterable) {
      result = (31 * result) + (object == null ? 0 : object.hashCode());
    }
    return result;
  }

  public static final class Entry {
    private final long id;
    private final Screen screen;

    private Entry(long id, Screen screen) {
      this.id = id;
      this.screen = screen;
    }

    public long getId() {
      return id;
    }

    public Screen getScreen() {
      return screen;
    }

    @Override public String toString() {
      return "{" + id + ", " + screen + "}";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Entry entry = (Entry) o;

      return id == entry.id && screen.equals(entry.screen);
    }

    @Override
    public int hashCode() {
      int result = (int) (id ^ (id >>> 32));
      result = 31 * result + screen.hashCode();
      return result;
    }
  }

  public static final class Builder {
    private long highestId;
    private final Deque<Entry> backstack;

    private Builder(long highestId, Collection<Entry> backstack) {
      this.highestId = highestId;
      this.backstack = new ArrayDeque<Entry>(backstack);
    }

    public Builder push(Screen screen) {
      backstack.push(new Entry(++highestId, screen));

      return this;
    }

    public Builder addAll(Collection<Screen> c) {
      for (Screen screen : c) {
        backstack.push(new Entry(++highestId, screen));
      }

      return this;
    }

    public Entry pop() {
      return backstack.pop();
    }

    public Builder clear() {
      backstack.clear();

      return this;
    }

    public Backstack build() {
      if (backstack.isEmpty()) {
        throw new IllegalStateException("Backstack may not be empty");
      }

      return new Backstack(highestId, backstack);
    }
  }

  private static class ReadIterator<T> implements Iterator<T> {
    private final Iterator<T> iterator;

    public ReadIterator(Iterator<T> iterator) {
      this.iterator = iterator;
    }

    @Override public boolean hasNext() {
      return iterator.hasNext();
    }

    @Override public T next() {
      return iterator.next();
    }

    @Override public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private interface ParcelableBackstack extends Parcelable {
    Backstack getBackstack(Parcer<Screen> parcer);

    Parcelable.Creator<ParcelableBackstack> CREATOR =
        new Parcelable.Creator<ParcelableBackstack>() {
          @Override public ParcelableBackstack createFromParcel(Parcel in) {
            List<ParcelableEntry> entries = new ArrayList<ParcelableEntry>();
            long highestId = in.readLong();
            in.readTypedList(entries, ParcelableEntry.CREATOR);
            return new Parcelled(highestId, entries);
          }

          @Override public ParcelableBackstack[] newArray(int size) {
            return new Parcelled[size];
          }
        };

    static class Memory implements ParcelableBackstack {
      private final Backstack backstack;
      private final Parcer<Screen> parcer;

      Memory(Backstack backstack, Parcer<Screen> parcer) {
        this.backstack = backstack;
        this.parcer = parcer;
      }

      @Override public Backstack getBackstack(Parcer<Screen> parcer) {
        return backstack;
      }

      @Override public int describeContents() {
        return 0;
      }

      @Override public void writeToParcel(Parcel out, int flags) {
        List<ParcelableEntry> entries = new ArrayList<ParcelableEntry>();
        for (Entry entry : backstack) {
          entries.add(new ParcelableEntry(entry.id, parcer.wrap(entry.getScreen())));
        }

        out.writeLong(backstack.highestId);
        out.writeTypedList(entries);
      }
    }

    static class Parcelled implements ParcelableBackstack {
      private final long highestId;
      private final List<ParcelableEntry> entries;

      Parcelled(long highestId, List<ParcelableEntry> entries) {
        this.highestId = highestId;
        this.entries = entries;
      }

      @Override public Backstack getBackstack(Parcer<Screen> parcer) {
        List<Entry> backstack = new ArrayList<Entry>();
        for (ParcelableEntry entry : entries) {
          backstack.add(entry.toRealEntry(parcer));
        }

        return new Builder(highestId, backstack).build();
      }

      @Override public int describeContents() {
        return 0;
      }

      @Override public void writeToParcel(Parcel out, int flags) {
        out.writeLong(highestId);
        out.writeTypedList(entries);
      }
    }
  }

  private static class ParcelableEntry implements Parcelable {
    private final long id;
    private final Parcelable parcelable;

    ParcelableEntry(long id, Parcelable parcelable) {
      this.id = id;
      this.parcelable = parcelable;
    }

    public Entry toRealEntry(Parcer<Screen> parcer) {
      return new Entry(id, parcer.unwrap(parcelable));
    }

    @Override public int describeContents() {
      return 0;
    }

    @Override public void writeToParcel(Parcel out, int flags) {
      out.writeLong(id);
      out.writeParcelable(parcelable, flags);
    }

    public static final Parcelable.Creator<ParcelableEntry> CREATOR =
        new Parcelable.Creator<ParcelableEntry>() {
          @Override public ParcelableEntry createFromParcel(Parcel in) {
            long id = in.readLong();
            Parcelable parcelable = in.readParcelable(getClass().getClassLoader());
            return new ParcelableEntry(id, parcelable);
          }

          @Override public ParcelableEntry[] newArray(int size) {
            return new ParcelableEntry[size];
          }
        };
  }
}
