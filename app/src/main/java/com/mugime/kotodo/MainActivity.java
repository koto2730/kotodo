package com.mugime.kotodo;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationView;
import com.mugime.kotodo.databinding.ActivityMainBinding;
import com.mugime.kotodo.ui.edit.TodoEditFragment;
import com.mugime.kotodo.ui.list.TodoListFragment;

import java.util.Objects;

/**
 * Hosts the navigation drawer and the single-activity navigation graph.
 *
 * <p>Screen menus are supplied by the fragments themselves through
 * {@code MenuProvider}; the activity only owns the drawer and the add button.</p>
 */
public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.appBarMain.toolbar);

        DrawerLayout drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_today, R.id.nav_all, R.id.nav_completed)
                .setOpenableLayout(drawer)
                .build();

        NavController navController = navController();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        // Sets up the standard "navigate + close drawer" behavior and keeps the
        // checked drawer item in sync with the current destination (including on
        // back-press). Overridden immediately below only to special-case 当日.
        NavigationUI.setupWithNavController(navigationView, navController);
        navigationView.setNavigationItemSelectedListener(item -> {
            boolean handled = NavigationUI.onNavDestinationSelected(item, navController);
            if (handled && item.getItemId() == R.id.nav_today) {
                // NavigationUI's default listener leaves the Today screen's date
                // bar wherever the user last navigated it to (launchSingleTop means
                // re-selecting 当日 while already there doesn't even recreate the
                // fragment). Force it back to today explicitly instead.
                getSupportFragmentManager().executePendingTransactions();
                resetTodayScreenIfShown();
            }
            drawer.closeDrawers();
            return handled;
        });

        binding.appBarMain.fab.setOnClickListener(view -> {
            Bundle args = new Bundle();
            args.putLong(TodoEditFragment.ARG_TODO_ID, 0L);
            navController().navigate(R.id.nav_edit, args);
        });

        // The add button belongs to the list screens only.
        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                binding.appBarMain.fab.setVisibility(
                        destination.getId() == R.id.nav_edit ? View.GONE : View.VISIBLE));
    }

    @Override
    public boolean onSupportNavigateUp() {
        return NavigationUI.navigateUp(navController(), appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    /**
     * The graph's controller.
     *
     * <p>Resolved through the fragment manager rather than
     * {@code Navigation.findNavController(activity, id)}: with a
     * {@code FragmentContainerView} the host fragment's view - and the tag that
     * lookup relies on - does not exist yet while {@code onCreate} runs.</p>
     */
    private NavController navController() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_main);
        return Objects.requireNonNull(host, "NavHostFragment is missing").getNavController();
    }

    private void resetTodayScreenIfShown() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_main);
        if (host == null) {
            return;
        }
        Fragment current = host.getChildFragmentManager().getPrimaryNavigationFragment();
        if (current instanceof TodoListFragment) {
            ((TodoListFragment) current).resetToToday();
        }
    }
}
