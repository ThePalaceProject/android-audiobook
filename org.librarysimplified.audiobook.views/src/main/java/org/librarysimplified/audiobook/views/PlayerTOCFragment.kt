package org.librarysimplified.audiobook.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewErrorsDownloadOpen
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationTOCClose
import org.slf4j.LoggerFactory

class PlayerTOCFragment : PlayerBaseFragment() {

  private val logger =
    LoggerFactory.getLogger(PlayerTOCFragment::class.java)

  private var subscriptions: CompositeDisposable = CompositeDisposable()

  private lateinit var menuErrors: MenuItem
  private lateinit var toolbarMenu: Menu
  private lateinit var menuCancelAll: MenuItem
  private lateinit var menuRefreshAll: MenuItem
  private lateinit var tabLayout: TabLayout
  private lateinit var toolbar: Toolbar
  private lateinit var viewPager: ViewPager2
  private lateinit var viewPagerAdapter: PlayerTOCAdapter

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    val view =
      inflater.inflate(R.layout.fragment_player_toc, container, false)

    this.tabLayout =
      view.findViewById(R.id.tocTabs)
    this.viewPager =
      view.findViewById(R.id.tocViewPager)
    this.viewPagerAdapter =
      PlayerTOCAdapter(
        parentFragment = this,
        fragments = listOf(
          PlayerTOCChaptersFragment(),
          PlayerTOCBookmarksFragment()
        ),
        fragmentTitles = listOf(
          getString(R.string.audiobook_player_menu_toc_title_chapters),
          getString(R.string.audiobook_player_menu_toc_title_bookmarks)
        )
      )
    this.viewPager.adapter = this.viewPagerAdapter

    this.toolbar = view.findViewById(R.id.tocToolbar)
    this.toolbar.setNavigationOnClickListener {
      PlayerModel.submitViewCommand(PlayerViewNavigationTOCClose)
    }
    this.toolbar.setNavigationContentDescription(R.string.audiobook_accessibility_navigation_back)
    this.toolbar.setNavigationIcon(R.drawable.back)
    this.toolbar.inflateMenu(R.menu.player_toc_menu)
    this.toolbarMenu = this.toolbar.menu
    this.menuCancelAll =
      this.toolbarMenu.findItem(R.id.player_toc_menu_stop_all)
    this.menuRefreshAll =
      this.toolbarMenu.findItem(R.id.player_toc_menu_refresh_all)
    this.menuErrors =
      this.toolbarMenu.findItem(R.id.player_toc_menu_error)

    this.menuCancelAll.setOnMenuItemClickListener {
      this.onMenuCancelAllSelected()
      true
    }
    this.menuRefreshAll.setOnMenuItemClickListener {
      this.onMenuRefreshAllSelected()
      true
    }
    this.menuErrors.setOnMenuItemClickListener {
      this.onMenuErrorsSelected()
      true
    }

    this.menuCancelAll.setVisible(false)
    this.menuRefreshAll.setVisible(false)
    this.menuErrors.setVisible(false)

    TabLayoutMediator(this.tabLayout, this.viewPager) { tab, position ->
      tab.text = this.viewPagerAdapter.getTitle(position)
    }.attach()
    return view
  }

  private fun onMenuErrorsSelected() {
    PlayerModel.submitViewCommand(PlayerViewErrorsDownloadOpen)
  }

  private fun onMenuRefreshAllSelected() {
    try {
      val book = PlayerModel.book()
      book.wholeBookDownloadTask.fetch()
    } catch (e: Exception) {
      this.logger.debug("onMenuRefreshAllSelected: ", e)
    }
  }

  private fun onMenuCancelAllSelected() {
    try {
      val book = PlayerModel.book()
      book.wholeBookDownloadTask.cancel()
    } catch (e: Exception) {
      this.logger.debug("onMenuCancelAllSelected: ", e)
    }
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
    this.subscriptions.add(
      PlayerModel.downloadEvents.subscribe {
        this.onDownloadStatusChanged()
      }
    )
  }

  override fun onStop() {
    super.onStop()

    this.subscriptions.dispose()
  }

  private fun onDownloadStatusChanged() {
    if (PlayerModel.isDownloading()) {
      this.menuRefreshAll.setVisible(false)
      this.menuCancelAll.setVisible(true)
    } else if (PlayerModel.isDownloadingCompleted()) {
      this.menuRefreshAll.setVisible(false)
      this.menuCancelAll.setVisible(false)
    } else {
      this.menuRefreshAll.setVisible(true)
      this.menuCancelAll.setVisible(false)
    }

    if (PlayerModel.isAnyDownloadingFailed()) {
      this.menuErrors.setVisible(true)
    } else {
      this.menuErrors.setVisible(false)
    }
  }
}
