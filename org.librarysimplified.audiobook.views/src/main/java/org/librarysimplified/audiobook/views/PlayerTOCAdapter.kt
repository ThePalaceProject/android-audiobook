package org.librarysimplified.audiobook.views

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class PlayerTOCAdapter(
  parentFragment: Fragment,
  private val fragments: List<Fragment>,
  private val fragmentTitles: List<String>
) : FragmentStateAdapter(parentFragment) {
  override fun getItemCount(): Int {
    return fragments.size
  }

  override fun createFragment(position: Int): Fragment {
    return fragments[position]
  }

  fun getTitle(position: Int): String {
    return fragmentTitles[position]
  }
}
