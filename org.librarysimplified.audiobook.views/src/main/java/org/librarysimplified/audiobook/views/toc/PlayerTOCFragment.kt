package org.librarysimplified.audiobook.views.toc

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.librarysimplified.audiobook.api.PlayerAudioBookType
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemDownloadStatus
import org.librarysimplified.audiobook.api.PlayerReadingOrderItemType
import org.librarysimplified.audiobook.api.PlayerType
import org.librarysimplified.audiobook.api.PlayerUIThread
import org.librarysimplified.audiobook.views.PlayerFragmentListenerType
import org.librarysimplified.audiobook.views.R
import org.slf4j.LoggerFactory

/**
 * A table of contents fragment.
 *
 * New instances MUST be created with {@link #newInstance()} rather than calling the constructor
 * directly. The public constructor only exists because the Android API requires it.
 *
 * Activities hosting this fragment MUST implement the {@link org.librarysimplified.audiobook.views.PlayerFragmentListenerType}
 * interface. An exception will be raised if this is not the case.
 */

class PlayerTOCFragment : Fragment(), PlayerTOCMainFragment {

  companion object {
    fun newInstance(): PlayerTOCFragment {
      return PlayerTOCFragment()
    }
  }

  private val log = LoggerFactory.getLogger(PlayerTOCFragment::class.java)

  private val pageChangeListener = object : ViewPager2.OnPageChangeCallback() {
    override fun onPageSelected(position: Int) {
      super.onPageSelected(position)
      if (!::menuCancelAll.isInitialized || !::menuRefreshAll.isInitialized) {
        return
      }
      menuRefreshAll.isVisible = position == 0
      menuCancelAll.isVisible = position == 0
    }
  }

  private lateinit var book: PlayerAudioBookType
  private lateinit var listener: PlayerFragmentListenerType
  private var menuInitialized = false
  private lateinit var menuRefreshAll: MenuItem
  private lateinit var menuCancelAll: MenuItem
  private lateinit var player: PlayerType
  private lateinit var viewPagerAdapter: PlayerTOCAdapter
  private lateinit var viewPager: ViewPager2
  private lateinit var tabLayout: TabLayout
  private lateinit var toolbar: Toolbar

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    return inflater.inflate(R.layout.fragment_player_toc, container, false)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    this.tabLayout =
      view.findViewById(R.id.tocTabs)
    this.viewPager =
      view.findViewById(R.id.tocViewPager)
    this.toolbar =
      view.findViewById(R.id.tocToolbar)
    this.viewPagerAdapter =
      PlayerTOCAdapter(
        parentFragment = this,
        fragments = listOf(
          PlayerTOCChaptersFragment.newInstance(),
          PlayerTOCBookmarksFragment.newInstance()
        ),
        fragmentTitles = listOf(
          getString(R.string.audiobook_player_menu_toc_title_chapters),
          getString(R.string.audiobook_player_menu_toc_title_bookmarks)
        )
      )

    this.viewPager.adapter = this.viewPagerAdapter
    this.viewPager.registerOnPageChangeCallback(pageChangeListener)

    this.toolbar.setNavigationOnClickListener { activity?.onBackPressed() }
    this.toolbar.setNavigationContentDescription(R.string.audiobook_accessibility_navigation_back)

    TabLayoutMediator(this.tabLayout, this.viewPager) { tab, position ->
      tab.text = this.viewPagerAdapter.getTitle(position)
    }.attach()
  }

  override fun onDestroyView() {
    this.viewPager.unregisterOnPageChangeCallback(pageChangeListener)
    super.onDestroyView()
  }

  override fun onCreate(state: Bundle?) {
    this.log.debug("onCreate")
    super.onCreate(state)

    /*
     * This fragment wants an options menu.
     */

    this.setHasOptionsMenu(true)
  }

  override fun onAttach(context: Context) {
    this.log.debug("onAttach")
    super.onAttach(context)

    if (context is PlayerFragmentListenerType) {
      this.listener = context

      this.book = this.listener.onPlayerTOCWantsBook()
      this.player = this.listener.onPlayerWantsPlayer()
    } else {
      throw ClassCastException(
        StringBuilder(64)
          .append("The activity hosting this fragment must implement one or more listener interfaces.\n")
          .append("  Activity: ")
          .append(context::class.java.canonicalName)
          .append('\n')
          .append("  Required interface: ")
          .append(PlayerFragmentListenerType::class.java.canonicalName)
          .append('\n')
          .toString()
      )
    }
  }

  override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
    this.log.debug("onCreateOptionsMenu")
    super.onCreateOptionsMenu(menu, inflater)

    inflater.inflate(R.menu.player_toc_menu, menu)

    this.menuRefreshAll = menu.findItem(R.id.player_toc_menu_refresh_all)
    this.menuCancelAll = menu.findItem(R.id.player_toc_menu_stop_all)
    this.menuInitialized = true
    this.menusConfigureVisibility()
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (val id = item.itemId) {
      R.id.player_toc_menu_refresh_all -> {
        childFragmentManager.fragments.filterIsInstance<PlayerTOCInnerFragment>().forEach {
          it.onMenuRefreshAllSelected()
        }
        true
      }
      R.id.player_toc_menu_stop_all -> {
        childFragmentManager.fragments.filterIsInstance<PlayerTOCInnerFragment>().forEach {
          it.onMenuStopAllSelected()
        }
        true
      }
      else -> {
        this.log.debug("unrecognized menu item: {}", id)
        false
      }
    }
  }

  override fun menusConfigureVisibility() {
    PlayerUIThread.checkIsUIThread()

    if (this.menuInitialized) {
      val refreshVisibleThen = this.menuRefreshAll.isVisible
      val cancelVisibleThen = this.menuCancelAll.isVisible

      val refreshVisibleNow =
        this.book.readingOrder.any { item -> isRefreshable(item) }
      val cancelVisibleNow =
        this.book.readingOrder.any { item -> isCancellable(item) }

      if (refreshVisibleNow != refreshVisibleThen || cancelVisibleNow != cancelVisibleThen) {
        this.menuRefreshAll.isVisible = refreshVisibleNow
        this.menuCancelAll.isVisible = cancelVisibleNow
        this.requireActivity().invalidateOptionsMenu()
      }
    }
  }

  private fun isCancellable(item: PlayerReadingOrderItemType): Boolean {
    return when (item.downloadStatus) {
      is PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadExpired -> false
      is PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadFailed -> false
      is PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded -> false
      is PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloading -> true
      is PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded -> false
    }
  }

  private fun isRefreshable(item: PlayerReadingOrderItemType): Boolean {
    return when (item.downloadStatus) {
      is PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadExpired -> false
      is PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloadFailed -> true
      is PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemNotDownloaded -> true
      is PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloading -> false
      is PlayerReadingOrderItemDownloadStatus.PlayerReadingOrderItemDownloaded -> false
    }
  }
}
