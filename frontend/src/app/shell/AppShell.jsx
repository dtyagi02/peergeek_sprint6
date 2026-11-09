import { useState } from "react";
import { Link, NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { navigationItems, visibleNavigation } from "../registry/navigation";
import { Slot } from "../registry/slots";
import { useAuthStore } from "../../features/auth/authStore";
import { InstructorActiveSessionBanner } from "../../features/doubtsession/InstructorActiveSessionBanner";

export function AppShell() {
  const [navSearch, setNavSearch] = useState("");
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const isPlayerRoute = location.pathname.includes("/learning/courses/") && location.pathname.includes("/lessons/");
  const isCommunityRoute = location.pathname === "/community";
  const isDomainCommunityRoute = /^\/domains\/[^/]+\/community$/.test(location.pathname);
  const isMessagesRoute = location.pathname.startsWith("/messages");

  const isAuthenticated = useAuthStore(
    (state) => state.status === "authenticated",
  );
  const user = useAuthStore((state) => state.user);
  const userMode = user?.lastMode ?? "STUDENT";
  const userRole = user?.role;
  const headerItems = visibleNavigation(navigationItems, {
    placement: "header",
    isAuthenticated,
    userMode,
    userRole,
  });
  const sidebarItems = visibleNavigation(navigationItems, {
    placement: "sidebar",
    isAuthenticated,
    userMode,
    userRole,
  });

  function handleNavSearchSubmit(e) {
    e.preventDefault();
    if (navSearch.trim()) {
      navigate(`/courses?q=${encodeURIComponent(navSearch.trim())}`);
      setNavSearch("");
      setMobileMenuOpen(false);
    } else {
      navigate("/courses");
      setMobileMenuOpen(false);
    }
  }

  return (
    <div className={`flex flex-col bg-surface-50 text-[#171717] ${
      isCommunityRoute || isDomainCommunityRoute || isMessagesRoute ? "h-[100dvh] overflow-hidden" : "min-h-screen"
    }`}>
      <a
        className="sr-only z-50 rounded bg-white p-3 focus:not-sr-only focus:absolute focus:left-4 focus:top-4 text-[#171717]"
        href="#main-content"
      >
        Skip to content
      </a>

      {/* Primary Navigation Bar (Charcoal Black #171717 surface) */}
      <header className="sticky top-0 z-40 border-b border-[#262626] bg-[#171717] text-white shadow-sm">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3.5 sm:px-6">
          {/* Left: Brand Identity & Primary Nav Links */}
          <div className="flex items-center gap-6">
            <Link
              aria-label="PeerGeek home"
              className="flex items-center gap-1 text-xl font-extrabold tracking-tight text-white focus:outline-none focus-visible:ring-2 focus-visible:ring-[#A31C1B] rounded"
              to="/"
            >
              <span>Peer</span>
              <span className="text-[#A31C1B]">Geek</span>
            </Link>

            <nav aria-label="Main navigation" className="hidden md:flex items-center gap-2">
              {headerItems.map((item) => (
                <NavLink
                  className={({ isActive }) =>
                    `rounded-lg px-3 py-1.5 text-sm font-medium transition-colors ${
                      isActive
                        ? "bg-[#06110C] text-white font-semibold border-b-2 border-[#A31C1B]"
                        : "text-neutral-300 hover:text-white hover:bg-neutral-800"
                    }`
                  }
                  key={item.id}
                  to={item.route}
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>

          {/* Center: Search Field (Reference Image 1) */}
          <form
            className="hidden lg:flex flex-1 max-w-md mx-4 relative"
            onSubmit={handleNavSearchSubmit}
            role="search"
          >
            <label className="sr-only" htmlFor="nav-search-input">
              What do you want to learn?
            </label>
            <input
              aria-label="What do you want to learn?"
              className="w-full rounded-full border border-neutral-700 bg-[#262626] pl-4 pr-10 py-1.5 text-sm text-white placeholder-neutral-400 focus:border-[#A31C1B] focus:outline-none focus:ring-1 focus:ring-[#A31C1B] transition-colors"
              id="nav-search-input"
              onChange={(e) => setNavSearch(e.target.value)}
              placeholder="What do you want to learn?"
              type="search"
              value={navSearch}
            />
            <button
              aria-label="Submit search"
              className="absolute right-1 top-1/2 -translate-y-1/2 rounded-full bg-[#A31C1B] p-1.5 text-white hover:bg-[#881716] focus:outline-none focus:ring-1 focus:ring-white transition-colors"
              type="submit"
            >
              <svg
                aria-hidden="true"
                className="h-3.5 w-3.5"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                <path
                  d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth="2.5"
                />
              </svg>
            </button>
          </form>

          {/* Right: Actions, Mode, Account */}
          <div className="flex items-center gap-3">
            <Slot name="header.actions" />

            {/* Mobile Menu Toggle Button */}
            <button
              aria-expanded={mobileMenuOpen}
              aria-label="Toggle navigation menu"
              className="md:hidden rounded-lg p-2 text-neutral-300 hover:bg-neutral-800 hover:text-white focus:outline-none focus:ring-2 focus:ring-[#A31C1B]"
              onClick={() => setMobileMenuOpen((prev) => !prev)}
              type="button"
            >
              <svg
                aria-hidden="true"
                className="h-6 w-6"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
              >
                {mobileMenuOpen ? (
                  <path
                    d="M6 18L18 6M6 6l12 12"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                  />
                ) : (
                  <path
                    d="M4 6h16M4 12h16M4 18h16"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2"
                  />
                )}
              </svg>
            </button>
          </div>
        </div>

        {/* Mobile Navigation Drawer */}
        {mobileMenuOpen && (
          <div className="border-t border-[#262626] bg-[#171717] px-4 py-4 md:hidden">
            {/* Mobile Search */}
            <form className="relative mb-4" onSubmit={handleNavSearchSubmit} role="search">
              <label className="sr-only" htmlFor="mobile-nav-search">
                Search courses
              </label>
              <input
                aria-label="Search courses"
                className="w-full rounded-lg border border-neutral-700 bg-[#262626] pl-4 pr-10 py-2 text-sm text-white placeholder-neutral-400 focus:border-[#A31C1B] focus:outline-none focus:ring-1 focus:ring-[#A31C1B]"
                id="mobile-nav-search"
                onChange={(e) => setNavSearch(e.target.value)}
                placeholder="What do you want to learn?"
                type="search"
                value={navSearch}
              />
              <button
                aria-label="Submit search"
                className="absolute right-2 top-1/2 -translate-y-1/2 rounded bg-[#A31C1B] p-1 text-white hover:bg-[#881716]"
                type="submit"
              >
                <svg
                  aria-hidden="true"
                  className="h-4 w-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth="2.5"
                  />
                </svg>
              </button>
            </form>

            <nav aria-label="Mobile navigation" className="space-y-1">
              {headerItems.map((item) => (
                <NavLink
                  className={({ isActive }) =>
                    `block rounded-lg px-3 py-2 text-base font-medium transition-colors ${
                      isActive
                        ? "bg-[#06110C] font-semibold text-white border-l-4 border-[#A31C1B]"
                        : "text-neutral-300 hover:bg-neutral-800 hover:text-white"
                    }`
                  }
                  key={item.id}
                  onClick={() => setMobileMenuOpen(false)}
                  to={item.route}
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </div>
        )}
      </header>

      <InstructorActiveSessionBanner />

      {/* Main Content Area */}
      <div
        className={`flex w-full flex-1 min-h-0 ${
          isPlayerRoute || isCommunityRoute || isDomainCommunityRoute || isMessagesRoute ? "" : "mx-auto max-w-7xl px-4 sm:px-6"
        } ${isCommunityRoute || isDomainCommunityRoute || isMessagesRoute ? "overflow-hidden" : ""}`}
      >
        {sidebarItems.length > 0 && !isPlayerRoute && (
          <aside className="hidden w-56 shrink-0 border-r border-surface-200 py-8 pr-5 md:block">
            <nav aria-label="Sidebar navigation" className="grid gap-2">
              {sidebarItems.map((item) => (
                <NavLink
                  className={({ isActive }) =>
                    `rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                      isActive
                        ? "bg-[#06110C] font-semibold text-white border-l-4 border-[#A31C1B] shadow-xs"
                        : "text-neutral-700 hover:bg-surface-100 hover:text-neutral-900"
                    }`
                  }
                  key={item.id}
                  to={item.route}
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </aside>
        )}
        <main
          className={`min-w-0 min-h-0 flex-1 ${isPlayerRoute || isCommunityRoute || isDomainCommunityRoute || isMessagesRoute ? "" : "py-8"} ${
            isCommunityRoute || isDomainCommunityRoute || isMessagesRoute ? "overflow-hidden" : ""
          }`}
          id="main-content"
        >
          <Outlet />
        </main>
      </div>

      {/* Footer (Charcoal Black #171717 surface) */}
      {!isCommunityRoute && !isDomainCommunityRoute && !isMessagesRoute && <footer className="border-t border-[#262626] bg-[#171717] text-white">
        <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
          <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
            <div className="flex items-center gap-1.5 text-lg font-bold">
              <span>Peer</span>
              <span className="text-[#A31C1B]">Geek</span>
              <span className="text-neutral-500 font-normal text-sm ml-2">
                · Learn together, grow together.
              </span>
            </div>
            <div className="flex items-center gap-6 text-xs text-neutral-400">
              <Link className="hover:text-white transition-colors" to="/courses">
                Explore Courses
              </Link>
              <Link className="hover:text-white transition-colors" to="/learning">
                My Learning
              </Link>
              <span>© {new Date().getFullYear()} PeerGeek</span>
            </div>
          </div>
        </div>
      </footer>}
    </div>
  );
}
