package org.librarysimplified.audiobook.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import io.reactivex.disposables.CompositeDisposable
import org.librarysimplified.audiobook.views.PlayerViewCommand.PlayerViewNavigationTOCClose

class PlayerTOCFragment2 : PlayerBaseFragment() {

  private var subscriptions: CompositeDisposable = CompositeDisposable()

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

    TabLayoutMediator(this.tabLayout, this.viewPager) { tab, position ->
      tab.text = this.viewPagerAdapter.getTitle(position)
    }.attach()
    return view
  }

  override fun onStart() {
    super.onStart()

    this.subscriptions = CompositeDisposable()
  }

  override fun onStop() {
    super.onStop()

    this.subscriptions.dispose()
  }
}
