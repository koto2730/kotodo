package com.mugime.kotodo;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.navigation.NavigationView;
import com.mugime.kotodo.databinding.ActivityMainBinding;
import com.mugime.kotodo.elements.Priority;
import com.mugime.kotodo.ui.edit.TodoEditFragment;
import com.mugime.kotodo.ui.list.QuickAddSheet;
import com.mugime.kotodo.ui.list.TodoListFragment;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Hosts the navigation drawer and the single-activity navigation graph.
 *
 * <p>Screen menus are supplied by the fragments themselves through
 * {@code MenuProvider}; the activity only owns the drawer and the add button.</p>
 */
public class MainActivity extends AppCompatActivity {

    private static final String QUICK_ADD_TAG = "quick_add";

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
            if (item.getItemId() == R.id.nav_today) {
                // NavigationUI's default listener leaves the Today screen's date bar
                // wherever the user last navigated it to. Deliver a Fragment Result on
                // the NavHostFragment's child FragmentManager so that whether the
                // Today fragment is already started or about to be restored/started by
                // Navigation Component, it receives the reset request lifecycle-safely.
                requestTodayReset();
            }
            drawer.closeDrawers();
            return handled;
        });

        // The add button opens the quick-add sheet; "Details" there continues in the full editor.
        binding.appBarMain.fab.setOnClickListener(view -> {
            if (getSupportFragmentManager().findFragmentByTag(QUICK_ADD_TAG) == null) {
                new QuickAddSheet().show(getSupportFragmentManager(), QUICK_ADD_TAG);
            }
        });

        // The add button belongs to the list screens only.
        navController.addOnDestinationChangedListener((controller, destination, arguments) ->
                binding.appBarMain.fab.setVisibility(
                        destination.getId() == R.id.nav_edit ? View.GONE : View.VISIBLE));
    }

    /**
     * Opens the full editor for a new todo, pre-filled with what the quick-add
     * sheet already collected.
     */
    public void openEditor(@NonNull String title, @Nullable LocalDate due, @NonNull Priority priority) {
        Bundle args = new Bundle();
        args.putLong(TodoEditFragment.ARG_TODO_ID, 0L);
        args.putString(TodoEditFragment.ARG_PREFILL_TITLE, title);
        args.putLong(TodoEditFragment.ARG_PREFILL_DUE_DAY,
                due == null ? TodoEditFragment.NO_DUE_DAY : due.toEpochDay());
        args.putInt(TodoEditFragment.ARG_PREFILL_PRIORITY, priority.ordinal());
        navController().navigate(R.id.nav_edit, args);
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

    private void requestTodayReset() {
        NavHostFragment host = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment_content_main);
        if (host == null) {
            return;
        }
        host.getChildFragmentManager().setFragmentResult(
                TodoListFragment.REQUEST_RESET_TODAY, new Bundle());
        Fragment current = host.getChildFragmentManager().getPrimaryNavigationFragment();
        if (current instanceof TodoListFragment) {
            ((TodoListFragment) current).resetToToday();
        }
    }
}
