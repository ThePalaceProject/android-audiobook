package org.librarysimplified.audiobook.views

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

/**
 * The adapter that allows for switching between the Chapters and Bookmarks tab in the TOC.
 */

class PlayerTOCAdapter(
  parentFragment: Fragment,
  private val fragments: List<Fragment>,
  private val fragmentTitles: List<String>
) : FragmentStateAdapter(parentFragment) {
  override fun getItemCount(): Int = fragments.size

  override fun createFragment(position: Int): Fragment = fragments[position]

  fun getTitle(position: Int): String = fragmentTitles[position]
}
