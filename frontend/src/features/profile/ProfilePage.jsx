import { useCallback, useEffect, useState, useRef } from "react";
import { useParams, Link, useNavigate } from "react-router-dom";
import { apiRequest } from "../../shared/api/client";
import { ApiError } from "../../shared/api/ApiError";
import { Button } from "../../shared/components/Button";
import { Dialog } from "../../shared/components/Dialog";
import { ErrorState } from "../../shared/components/ErrorState";
import { Spinner } from "../../shared/components/Spinner";
import { useToast } from "../../shared/components/useToast";

export function ProfilePage() {
  const { username: routeUsername } = useParams();
  const toast = useToast();
  const navigate = useNavigate();

  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  // Modals state
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [eduModalOpen, setEduModalOpen] = useState(false);
  const [editingEdu, setEditingEdu] = useState(null);
  const [projModalOpen, setProjModalOpen] = useState(false);
  const [editingProj, setEditingProj] = useState(null);

  // Avatar upload
  const fileInputRef = useRef(null);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);

  // Edit Profile form state
  const [editForm, setEditForm] = useState({
    displayName: "",
    username: "",
    headline: "",
    bio: "",
    skills: [],
    interests: "",
    teachingExpertise: "",
    githubUrl: "",
    linkedinUrl: "",
    portfolioUrl: "",
    websiteUrl: "",
    showEducation: true,
    showSocialLinks: true,
    showLearningHistory: true,
  });
  const [newSkillInput, setNewSkillInput] = useState("");
  const [skillInputError, setSkillInputError] = useState("");
  const [editFormErrors, setEditFormErrors] = useState({});
  const [savingProfile, setSavingProfile] = useState(false);

  // Education form state
  const [eduForm, setEduForm] = useState({
    institution: "",
    degree: "",
    fieldOfStudy: "",
    startYear: "",
    endYear: "",
  });
  const [eduErrors, setEduErrors] = useState({});
  const [savingEdu, setSavingEdu] = useState(false);

  // Project form state
  const [projForm, setProjForm] = useState({
    title: "",
    description: "",
    technologies: "",
    projectUrl: "",
    repoUrl: "",
  });
  const [projErrors, setProjErrors] = useState({});
  const [savingProj, setSavingProj] = useState(false);

  const fetchProfile = useCallback(async () => {
    try {
      setLoading(true);
      setLoadError(false);
      const endpoint = routeUsername
        ? `users/by-username/${encodeURIComponent(routeUsername)}/profile`
        : "users/me/profile";
      const data = await apiRequest(endpoint);
      setProfile(data);
    } catch {
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, [routeUsername]);

  useEffect(() => {
    Promise.resolve().then(fetchProfile);
  }, [fetchProfile]);

  const openEditModal = () => {
    if (!profile) return;
    setEditForm({
      displayName: profile.displayName || "",
      username: profile.username || "",
      headline: profile.headline || "",
      bio: profile.bio || "",
      skills: profile.skills ? [...profile.skills] : [],
      interests: (profile.interests || []).join(", "),
      teachingExpertise: profile.teachingExpertise || "",
      githubUrl: profile.githubUrl || "",
      linkedinUrl: profile.linkedinUrl || "",
      portfolioUrl: profile.portfolioUrl || "",
      websiteUrl: profile.websiteUrl || "",
      showEducation: profile.showEducation !== false,
      showSocialLinks: profile.showSocialLinks !== false,
      showLearningHistory: profile.showLearningHistory !== false,
    });
    setNewSkillInput("");
    setSkillInputError("");
    setEditFormErrors({});
    setEditModalOpen(true);
  };

  const handleAddDeclaredSkill = () => {
    setSkillInputError("");
    const trimmed = newSkillInput.trim();
    if (!trimmed) return;
    if (trimmed.length > 50) {
      setSkillInputError("Skill name must not exceed 50 characters.");
      return;
    }
    if (trimmed.includes("<") || trimmed.includes(">")) {
      setSkillInputError("HTML markup is not allowed in skills.");
      return;
    }
    const exists = editForm.skills.some(
      (s) => s.toLowerCase() === trimmed.toLowerCase(),
    );
    if (exists) {
      setSkillInputError("This skill has already been added.");
      return;
    }
    setEditForm((prev) => ({
      ...prev,
      skills: [...prev.skills, trimmed],
    }));
    setNewSkillInput("");
  };

  const handleRemoveDeclaredSkill = (indexToRemove) => {
    setEditForm((prev) => ({
      ...prev,
      skills: prev.skills.filter((_, idx) => idx !== indexToRemove),
    }));
  };

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setEditFormErrors({});
    setSavingProfile(true);

    const payload = {
      displayName: editForm.displayName.trim(),
      username: editForm.username.trim().toLowerCase(),
      headline: editForm.headline.trim() || null,
      bio: editForm.bio.trim() || null,
      skills: editForm.skills,
      interests: editForm.interests
        ? editForm.interests
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean)
        : [],
      teachingExpertise: editForm.teachingExpertise.trim() || null,
      githubUrl: editForm.githubUrl.trim() || null,
      linkedinUrl: editForm.linkedinUrl.trim() || null,
      portfolioUrl: editForm.portfolioUrl.trim() || null,
      websiteUrl: editForm.websiteUrl.trim() || null,
      showEducation: Boolean(editForm.showEducation),
      showSocialLinks: Boolean(editForm.showSocialLinks),
      showLearningHistory: Boolean(editForm.showLearningHistory),
    };

    try {
      const updated = await apiRequest("users/me/profile", {
        method: "PUT",
        body: payload,
      });
      setProfile((prev) => ({
        ...prev,
        ...updated,
      }));
      setEditModalOpen(false);
      toast.notify({
        title: "Profile updated",
        description: "Your profile information and settings have been saved.",
      });
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors && err.fieldErrors.length > 0) {
        const errorMap = {};
        for (const fe of err.fieldErrors) {
          errorMap[fe.field] = fe.message;
        }
        setEditFormErrors(errorMap);
      } else {
        setEditFormErrors({
          root: err.message || "Failed to save profile. Please try again.",
        });
      }
    } finally {
      setSavingProfile(false);
    }
  };

  const handleAvatarFileChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith("image/")) {
      toast.notify({
        title: "Invalid file",
        description: "Please select an image file (PNG, JPEG, WebP).",
        variant: "danger",
      });
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      toast.notify({
        title: "File too large",
        description: "Profile picture must not exceed 5 MB.",
        variant: "danger",
      });
      return;
    }

    const formData = new FormData();
    formData.append("file", file);

    setUploadingAvatar(true);
    try {
      const res = await apiRequest("users/me/avatar", {
        method: "POST",
        body: formData,
      });
      setProfile((prev) => ({ ...prev, avatarUrl: res.avatarUrl }));
      toast.notify({
        title: "Avatar updated",
        description: "Your new profile picture has been uploaded.",
      });
    } catch (err) {
      toast.notify({
        title: "Upload failed",
        description: err.message || "Could not upload image. Please try again.",
        variant: "danger",
      });
    } finally {
      setUploadingAvatar(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const handleDeleteAvatar = async () => {
    if (!window.confirm("Are you sure you want to remove your profile picture?")) return;
    try {
      await apiRequest("users/me/avatar", { method: "DELETE" });
      setProfile((prev) => ({ ...prev, avatarUrl: null }));
      toast.notify({
        title: "Avatar removed",
        description: "Your profile picture has been removed.",
      });
    } catch (err) {
      toast.notify({
        title: "Error",
        description: err.message || "Could not remove avatar.",
        variant: "danger",
      });
    }
  };

  const handleMessageUser = async () => {
    if (!profile?.id || isOwner) return;
    try {
      const response = await apiRequest(`direct-messages/conversations/${profile.id}`, {
        method: "POST",
      });
      const conversationId = response?.id || response?.conversationId;
      if (!conversationId) throw new Error("The conversation could not be opened.");
      navigate(`/messages/${conversationId}`);
    } catch (err) {
      toast.notify({
        title: "Unable to start conversation",
        description: err.message || "Please try again.",
        variant: "danger",
      });
    }
  };

  // Education CRUD
  const openAddEducation = () => {
    setEditingEdu(null);
    setEduForm({ institution: "", degree: "", fieldOfStudy: "", startYear: "", endYear: "" });
    setEduErrors({});
    setEduModalOpen(true);
  };

  const openEditEducation = (edu) => {
    setEditingEdu(edu);
    setEduForm({
      institution: edu.institution || "",
      degree: edu.degree || "",
      fieldOfStudy: edu.fieldOfStudy || "",
      startYear: edu.startYear ? String(edu.startYear) : "",
      endYear: edu.endYear ? String(edu.endYear) : "",
    });
    setEduErrors({});
    setEduModalOpen(true);
  };

  const handleSaveEducation = async (e) => {
    e.preventDefault();
    setEduErrors({});
    setSavingEdu(true);

    const payload = {
      institution: eduForm.institution.trim(),
      degree: eduForm.degree.trim() || null,
      fieldOfStudy: eduForm.fieldOfStudy.trim() || null,
      startYear: eduForm.startYear ? parseInt(eduForm.startYear, 10) : null,
      endYear: eduForm.endYear ? parseInt(eduForm.endYear, 10) : null,
    };

    try {
      if (editingEdu) {
        const updated = await apiRequest(`users/me/educations/${editingEdu.id}`, {
          method: "PUT",
          body: payload,
        });
        setProfile((prev) => ({
          ...prev,
          educations: prev.educations.map((ed) =>
            ed.id === editingEdu.id ? updated : ed,
          ),
        }));
        toast.notify({ title: "Education updated" });
      } else {
        const created = await apiRequest("users/me/educations", {
          method: "POST",
          body: payload,
        });
        setProfile((prev) => ({
          ...prev,
          educations: [created, ...(prev.educations || [])],
        }));
        toast.notify({ title: "Education added" });
      }
      setEduModalOpen(false);
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) {
        const map = {};
        for (const fe of err.fieldErrors) map[fe.field] = fe.message;
        setEduErrors(map);
      } else {
        setEduErrors({ root: err.message || "Failed to save education." });
      }
    } finally {
      setSavingEdu(false);
    }
  };

  const handleDeleteEducation = async (id) => {
    if (!window.confirm("Are you sure you want to remove this education record?")) return;
    try {
      await apiRequest(`users/me/educations/${id}`, { method: "DELETE" });
      setProfile((prev) => ({
        ...prev,
        educations: prev.educations.filter((ed) => ed.id !== id),
      }));
      toast.notify({ title: "Education removed" });
    } catch (err) {
      toast.notify({ title: "Error", description: err.message, variant: "danger" });
    }
  };

  // Project CRUD
  const openAddProject = () => {
    setEditingProj(null);
    setProjForm({ title: "", description: "", technologies: "", projectUrl: "", repoUrl: "" });
    setProjErrors({});
    setProjModalOpen(true);
  };

  const openEditProject = (proj) => {
    setEditingProj(proj);
    setProjForm({
      title: proj.title || "",
      description: proj.description || "",
      technologies: proj.technologies || "",
      projectUrl: proj.projectUrl || "",
      repoUrl: proj.repoUrl || "",
    });
    setProjErrors({});
    setProjModalOpen(true);
  };

  const handleSaveProject = async (e) => {
    e.preventDefault();
    setProjErrors({});
    setSavingProj(true);

    const payload = {
      title: projForm.title.trim(),
      description: projForm.description.trim() || null,
      technologies: projForm.technologies.trim() || null,
      projectUrl: projForm.projectUrl.trim() || null,
      repoUrl: projForm.repoUrl.trim() || null,
    };

    try {
      if (editingProj) {
        const updated = await apiRequest(`users/me/projects/${editingProj.id}`, {
          method: "PUT",
          body: payload,
        });
        setProfile((prev) => ({
          ...prev,
          projects: prev.projects.map((p) =>
            p.id === editingProj.id ? updated : p,
          ),
        }));
        toast.notify({ title: "Project updated" });
      } else {
        const created = await apiRequest("users/me/projects", {
          method: "POST",
          body: payload,
        });
        setProfile((prev) => ({
          ...prev,
          projects: [created, ...(prev.projects || [])],
        }));
        toast.notify({ title: "Project added" });
      }
      setProjModalOpen(false);
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors) {
        const map = {};
        for (const fe of err.fieldErrors) map[fe.field] = fe.message;
        setProjErrors(map);
      } else {
        setProjErrors({ root: err.message || "Failed to save project." });
      }
    } finally {
      setSavingProj(false);
    }
  };

  const handleDeleteProject = async (id) => {
    if (!window.confirm("Are you sure you want to remove this project?")) return;
    try {
      await apiRequest(`users/me/projects/${id}`, { method: "DELETE" });
      setProfile((prev) => ({
        ...prev,
        projects: prev.projects.filter((p) => p.id !== id),
      }));
      toast.notify({ title: "Project removed" });
    } catch (err) {
      toast.notify({ title: "Error", description: err.message, variant: "danger" });
    }
  };

  if (loading) return <Spinner label="Loading profile..." />;
  if (loadError || !profile) {
    return (
      <ErrorState
        description="The requested user profile does not exist or could not be loaded."
        title="Profile unavailable"
      />
    );
  }

  const isOwner = Boolean(profile.isOwner);
  const initials = (profile.displayName || "User")
    .split(" ")
    .map((w) => w[0])
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase();

  const isInstructor =
    profile.lastMode === "INSTRUCTOR" ||
    profile.role === "INSTRUCTOR" ||
    Boolean(profile.teachingExpertise) ||
    Boolean(profile.instructorStats);

  const teachingStats = profile.instructorStats || {
    totalPublishedCourses: 0,
    totalLearners: 0,
    averageRating: 0.0,
    totalReviews: 0,
    publishedCourses: [],
  };

  const publishedCoursesList = teachingStats.publishedCourses || [];

  const learningStats = profile.learningStats || {
    totalEnrolledCourses: 0,
    coursesCompleted: 0,
    coursesInProgress: 0,
    lessonsCompleted: 0,
  };

  const enrolledCoursesList = profile.enrolledCourses || [];
  const earnedSkillsList = profile.earnedSkills || [];
  const declaredSkillsList = profile.skills || [];

  return (
    <div className="mx-auto max-w-5xl space-y-10 pb-20">
      {/* 1. Common Profile Header and Basic Information */}
      <section className="relative overflow-hidden rounded-2xl border border-surface-200 bg-white p-6 shadow-sm sm:p-8">
        <div className="flex flex-col items-center gap-6 sm:flex-row sm:items-start">
          {/* Avatar with optional upload */}
          <div className="relative group shrink-0">
            {profile.avatarUrl ? (
              <img
                alt={profile.displayName}
                className="h-28 w-28 rounded-full border-2 border-brand-100 object-cover shadow-sm sm:h-32 sm:w-32"
                src={profile.avatarUrl}
              />
            ) : (
              <div className="flex h-28 w-28 items-center justify-center rounded-full bg-brand-100 text-3xl font-bold text-brand-800 shadow-sm sm:h-32 sm:w-32">
                {initials}
              </div>
            )}

            {isOwner && (
              <>
                <input
                  accept="image/png,image/jpeg,image/webp"
                  className="hidden"
                  onChange={handleAvatarFileChange}
                  ref={fileInputRef}
                  type="file"
                />
                <button
                  className="absolute bottom-0 right-0 flex h-9 w-9 items-center justify-center rounded-full bg-brand-600 text-white shadow hover:bg-brand-700 transition"
                  disabled={uploadingAvatar}
                  onClick={() => fileInputRef.current?.click()}
                  title="Upload avatar"
                  type="button"
                >
                  <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                    <path d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z" />
                    <path d="M15 13a3 3 0 11-6 0 3 3 0 016 0z" />
                  </svg>
                </button>
              </>
            )}
          </div>

          {/* User Details */}
          <div className="flex-1 text-center sm:text-left">
            <div className="flex flex-col items-center justify-between gap-4 sm:flex-row sm:items-start">
              <div>
                <div className="flex flex-wrap items-center justify-center gap-2 sm:justify-start">
                  <h1 className="text-2xl font-bold text-ink-900 sm:text-3xl">
                    {profile.displayName}
                  </h1>
                  <span className="rounded-full bg-surface-100 px-2.5 py-0.5 text-xs font-semibold text-ink-600">
                    @{profile.username}
                  </span>
                  {isInstructor ? (
                    <span className="rounded-full bg-emerald-50 px-2.5 py-0.5 text-xs font-semibold text-emerald-700 border border-emerald-200">
                      Instructor
                    </span>
                  ) : (
                    <span className="rounded-full bg-blue-50 px-2.5 py-0.5 text-xs font-semibold text-blue-700 border border-blue-200">
                      Student
                    </span>
                  )}
                </div>

                {profile.headline ? (
                  <p className="mt-1.5 text-base font-medium text-ink-700">
                    {profile.headline}
                  </p>
                ) : (
                  isOwner && (
                    <p className="mt-1.5 text-sm italic text-ink-400">
                      Add a professional or academic headline...
                    </p>
                  )
                )}

                {/* Email (Private, visible only to profile owner) */}
                {isOwner && profile.email && (
                  <p className="mt-1 text-xs text-ink-500">
                    Account: {profile.email} (Private)
                  </p>
                )}
              </div>

              {/* Header Action Buttons */}
              {isOwner && (
                <div className="flex items-center gap-2">
                  <Button onClick={openEditModal} variant="primary">
                    Edit Profile
                  </Button>
                  {profile.avatarUrl && (
                    <Button onClick={handleDeleteAvatar} variant="secondary">
                      Remove Avatar
                    </Button>
                  )}
                </div>
              )}
              {!isOwner && (
                <Button onClick={handleMessageUser} variant="primary">
                  Message
                </Button>
              )}
            </div>

            {/* Public Links */}
            {(profile.githubUrl || profile.linkedinUrl || profile.portfolioUrl || profile.websiteUrl) && (
              <div className="mt-4 flex flex-wrap items-center justify-center gap-2.5 sm:justify-start">
                {profile.githubUrl && (
                  <a
                    className="inline-flex items-center gap-1.5 rounded-lg border border-surface-200 bg-surface-50 px-3 py-1.5 text-xs font-medium text-ink-700 hover:bg-surface-100 hover:text-ink-900 transition"
                    href={profile.githubUrl}
                    rel="noreferrer noopener"
                    target="_blank"
                  >
                    <svg className="h-4 w-4" fill="currentColor" viewBox="0 0 24 24">
                      <path fillRule="evenodd" d="M12 2C6.477 2 2 6.484 2 12.017c0 4.425 2.865 8.18 6.839 9.504.5.092.682-.217.682-.483 0-.237-.008-.868-.013-1.703-2.782.605-3.369-1.343-3.369-1.343-.454-1.158-1.11-1.466-1.11-1.466-.908-.62.069-.608.069-.608 1.003.07 1.53 1.032 1.53 1.032.892 1.53 2.341 1.088 2.91.832.092-.647.35-1.088.636-1.338-2.22-.253-4.555-1.113-4.555-4.951 0-1.093.39-1.988 1.029-2.688-.103-.253-.446-1.272.098-2.65 0 0 .84-.27 2.75 1.026A9.564 9.564 0 0112 6.844c.85.004 1.705.115 2.504.337 1.909-1.296 2.747-1.027 2.747-1.027.546 1.379.202 2.398.1 2.651.64.7 1.028 1.595 1.028 2.688 0 3.848-2.339 4.695-4.566 4.943.359.309.678.92.678 1.855 0 1.338-.012 2.419-.012 2.747 0 .268.18.58.688.482A10.019 10.019 0 0022 12.017C22 6.484 17.522 2 12 2z" clipRule="evenodd" />
                    </svg>
                    GitHub
                  </a>
                )}
                {profile.linkedinUrl && (
                  <a
                    className="inline-flex items-center gap-1.5 rounded-lg border border-surface-200 bg-surface-50 px-3 py-1.5 text-xs font-medium text-ink-700 hover:bg-surface-100 hover:text-ink-900 transition"
                    href={profile.linkedinUrl}
                    rel="noreferrer noopener"
                    target="_blank"
                  >
                    <svg className="h-4 w-4 text-blue-600" fill="currentColor" viewBox="0 0 24 24">
                      <path d="M19 0h-14c-2.761 0-5 2.239-5 5v14c0 2.761 2.239 5 5 5h14c2.762 0 5-2.239 5-5v-14c0-2.761-2.238-5-5-5zm-11 19h-3v-11h3v11zm-1.5-12.268c-.966 0-1.75-.79-1.75-1.764s.784-1.764 1.75-1.764 1.75.79 1.75 1.764-.783 1.764-1.75 1.764zm13.5 12.268h-3v-5.604c0-3.368-4-3.113-4 0v5.604h-3v-11h3v1.765c1.396-2.586 7-2.777 7 2.476v6.759z" />
                    </svg>
                    LinkedIn
                  </a>
                )}
                {profile.portfolioUrl && (
                  <a
                    className="inline-flex items-center gap-1.5 rounded-lg border border-surface-200 bg-surface-50 px-3 py-1.5 text-xs font-medium text-ink-700 hover:bg-surface-100 hover:text-ink-900 transition"
                    href={profile.portfolioUrl}
                    rel="noreferrer noopener"
                    target="_blank"
                  >
                    Portfolio ↗
                  </a>
                )}
                {profile.websiteUrl && (
                  <a
                    className="inline-flex items-center gap-1.5 rounded-lg border border-surface-200 bg-surface-50 px-3 py-1.5 text-xs font-medium text-ink-700 hover:bg-surface-100 hover:text-ink-900 transition"
                    href={profile.websiteUrl}
                    rel="noreferrer noopener"
                    target="_blank"
                  >
                    Website ↗
                  </a>
                )}
              </div>
            )}
          </div>
        </div>

        {/* Bio & Interests */}
        <div className="mt-6 border-t border-surface-100 pt-6">
          <h2 className="text-sm font-semibold text-ink-900">About</h2>
          <div className="mt-2 text-sm text-ink-700 whitespace-pre-wrap leading-relaxed">
            {profile.bio || (
              <p className="italic text-ink-400">
                {isOwner
                  ? "You haven't written a biography yet. Click Edit Profile to tell others about yourself!"
                  : "No biography provided."}
              </p>
            )}
          </div>

          {profile.interests && profile.interests.length > 0 && (
            <div className="mt-4">
              <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-500">Interests</h3>
              <div className="mt-1.5 flex flex-wrap gap-1.5">
                {profile.interests.map((it, idx) => (
                  <span
                    className="rounded-md bg-surface-100 px-2.5 py-1 text-xs font-medium text-ink-700 border border-surface-200"
                    key={idx}
                  >
                    {it}
                  </span>
                ))}
              </div>
            </div>
          )}

          {profile.teachingExpertise && (
            <div className="mt-4">
              <h3 className="text-xs font-semibold uppercase tracking-wider text-ink-500">Teaching Expertise</h3>
              <p className="mt-1 text-sm text-ink-700 whitespace-pre-wrap">
                {profile.teachingExpertise}
              </p>
            </div>
          )}
        </div>
      </section>

      {/* 2. Section One: TEACHING */}
      <section className="rounded-2xl border border-surface-200 bg-white p-6 shadow-sm sm:p-8">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 border-b border-surface-100 pb-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-50 text-emerald-700 font-bold border border-emerald-200">
              <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
              </svg>
            </div>
            <div>
              <h2 className="text-xl font-bold tracking-tight text-ink-900 sm:text-2xl">
                TEACHING
              </h2>
              <p className="text-xs text-ink-500">
                Course instruction, published content, and student feedback.
              </p>
            </div>
          </div>

          {isOwner && (
            <div className="flex items-center gap-2">
              <Link
                to="/instructor/courses/new"
                className="inline-flex items-center justify-center rounded-lg bg-emerald-600 px-3.5 py-1.5 text-xs font-semibold text-white shadow-xs hover:bg-emerald-700 transition"
              >
                + Create Course
              </Link>
              <Link
                to="/instructor"
                className="inline-flex items-center justify-center rounded-lg border border-surface-200 bg-white px-3.5 py-1.5 text-xs font-semibold text-ink-700 hover:bg-surface-50 transition"
              >
                Instructor Dashboard
              </Link>
            </div>
          )}
        </div>

        {/* Instructor Overview Stats */}
        <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <div className="rounded-xl border border-surface-200 bg-surface-50 p-4 text-center">
            <p className="text-2xl font-bold text-ink-900">
              {teachingStats.totalPublishedCourses}
            </p>
            <p className="mt-1 text-xs font-medium text-ink-500">Published Courses</p>
          </div>

          <div className="rounded-xl border border-surface-200 bg-surface-50 p-4 text-center">
            <p className="text-2xl font-bold text-ink-900">
              {teachingStats.totalLearners ?? teachingStats.totalStudents ?? 0}
            </p>
            <p className="mt-1 text-xs font-medium text-ink-500">Total Learners</p>
          </div>

          <div className="rounded-xl border border-surface-200 bg-surface-50 p-4 text-center">
            {(teachingStats.totalReviews || teachingStats.reviewCount || 0) > 0 ? (
              <div className="flex items-center justify-center gap-1">
                <span className="text-2xl font-bold text-ink-900">
                  {teachingStats.averageRating}
                </span>
                <span className="text-amber-500 text-lg">★</span>
              </div>
            ) : (
              <p className="text-sm font-semibold text-ink-400 mt-1.5">No reviews yet</p>
            )}
            <p className="mt-1 text-xs font-medium text-ink-500">Instructor Rating</p>
          </div>

          <div className="rounded-xl border border-surface-200 bg-surface-50 p-4 text-center">
            <p className="text-2xl font-bold text-ink-900">
              {teachingStats.totalReviews ?? teachingStats.reviewCount ?? 0}
            </p>
            <p className="mt-1 text-xs font-medium text-ink-500">Course Reviews</p>
          </div>
        </div>

        {/* Teaching Course Cards */}
        <div className="mt-8">
          <h3 className="text-sm font-semibold text-ink-900 mb-4">
            {isOwner ? "My Teaching Courses" : "Published Courses by Instructor"}
          </h3>

          {publishedCoursesList.length > 0 ? (
            <div className="grid gap-4 sm:grid-cols-2">
              {publishedCoursesList.map((c) => {
                const isDraft = c.status === "DRAFT";
                return (
                  <article
                    key={c.id}
                    className="flex flex-col justify-between overflow-hidden rounded-xl border border-surface-200 bg-white p-5 shadow-xs transition hover:border-brand-300 hover:shadow-sm"
                  >
                    <div>
                      {/* Thumbnail or placeholder */}
                      <div className="relative mb-3 h-32 w-full overflow-hidden rounded-lg bg-surface-100">
                        {c.thumbnailUrl ? (
                          <img
                            src={c.thumbnailUrl}
                            alt={c.title}
                            className="h-full w-full object-cover"
                          />
                        ) : (
                          <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-emerald-600 to-teal-700 p-4 text-center">
                            <span className="text-sm font-bold text-white line-clamp-2">
                              {c.title}
                            </span>
                          </div>
                        )}
                        {/* Status badges */}
                        <div className="absolute top-2 right-2 flex gap-1">
                          {isOwner && (
                            <span
                              className={`rounded-md px-2 py-0.5 text-xs font-semibold shadow-xs ${
                                isDraft
                                  ? "bg-amber-500 text-white"
                                  : "bg-emerald-600 text-white"
                              }`}
                            >
                              {isDraft ? "Draft" : "Published"}
                            </span>
                          )}
                        </div>
                      </div>

                      <div className="flex items-start justify-between gap-2">
                        <Link
                          to={`/courses/${c.id}`}
                          className="font-bold text-ink-900 line-clamp-1 hover:text-brand-600 transition"
                        >
                          {c.title}
                        </Link>
                        {c.difficulty && (
                          <span className="rounded bg-surface-100 px-2 py-0.5 text-xs font-medium text-ink-600 shrink-0">
                            {c.difficulty}
                          </span>
                        )}
                      </div>

                      {c.description && (
                        <p className="mt-1.5 text-xs text-ink-600 line-clamp-2">
                          {c.description}
                        </p>
                      )}

                      <div className="mt-3 flex items-center justify-between text-xs text-ink-500">
                        <span>{c.enrolledCount ?? 0} learners enrolled</span>
                        {c.reviewCount > 0 ? (
                          <Link
                            to={`/courses/${c.id}/reviews`}
                            className="inline-flex items-center gap-1 font-medium text-amber-600 hover:underline"
                          >
                            <span>★ {c.averageRating}</span>
                            <span className="text-ink-400">({c.reviewCount})</span>
                          </Link>
                        ) : (
                          <span className="text-ink-400">No reviews yet</span>
                        )}
                      </div>
                    </div>

                    <div className="mt-4 flex items-center justify-between border-t border-surface-100 pt-3 text-xs">
                      <div className="flex items-center gap-3">
                        <Link
                          to={`/courses/${c.id}`}
                          className="font-medium text-brand-600 hover:underline"
                        >
                          View Details →
                        </Link>
                        <Link
                          to={`/courses/${c.id}/reviews`}
                          className="font-medium text-ink-600 hover:text-ink-900 hover:underline"
                        >
                          Reviews
                        </Link>
                      </div>

                      {isOwner && (
                        <Link
                          to={`/instructor/courses/${c.id}`}
                          className="rounded-md border border-surface-200 bg-surface-50 px-2.5 py-1 font-semibold text-ink-700 hover:bg-surface-100 transition"
                        >
                          Manage Course
                        </Link>
                      )}
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            <div className="rounded-xl border border-dashed border-surface-200 p-8 text-center">
              <p className="text-sm font-medium text-ink-700">
                {isOwner
                  ? "You haven't published any courses yet."
                  : "This user hasn't published any courses yet."}
              </p>
              {isOwner && (
                <div className="mt-4 flex justify-center gap-3">
                  <Link
                    to="/instructor/courses/new"
                    className="inline-flex items-center justify-center rounded-lg bg-emerald-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-emerald-700 transition"
                  >
                    Create Your First Course
                  </Link>
                  <Link
                    to="/instructor"
                    className="inline-flex items-center justify-center rounded-lg border border-surface-200 bg-white px-4 py-2 text-xs font-semibold text-ink-700 hover:bg-surface-50 transition"
                  >
                    Go to Instructor Hub
                  </Link>
                </div>
              )}
            </div>
          )}
        </div>
      </section>

      {/* 3. Section Two: LEARNING */}
      <section className="rounded-2xl border border-surface-200 bg-white p-6 shadow-sm sm:p-8">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 border-b border-surface-100 pb-4">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 text-blue-700 font-bold border border-blue-200">
              <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                <path d="M12 14l9-5-9-5-9 5 9 5z" />
                <path d="M12 14l6.16-3.422a12.083 12.083 0 01.665 6.479A11.952 11.952 0 0012 20.055a11.952 11.952 0 00-6.824-2.998 12.078 12.078 0 01.665-6.479L12 14z" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 14v7" />
              </svg>
            </div>
            <div>
              <h2 className="text-xl font-bold tracking-tight text-ink-900 sm:text-2xl">
                LEARNING
              </h2>
              <p className="text-xs text-ink-500">
                Enrollment progress, lesson completion milestones, and skills.
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Link
              to="/courses"
              className="inline-flex items-center justify-center rounded-lg bg-brand-600 px-3.5 py-1.5 text-xs font-semibold text-white shadow-xs hover:bg-brand-700 transition"
            >
              Explore Courses
            </Link>
            {isOwner && (
              <Link
                to="/learning"
                className="inline-flex items-center justify-center rounded-lg border border-surface-200 bg-white px-3.5 py-1.5 text-xs font-semibold text-ink-700 hover:bg-surface-50 transition"
              >
                My Learning Dashboard
              </Link>
            )}
          </div>
        </div>

        {/* Privacy Check for Public View */}
        {!isOwner && profile.showLearningHistory === false ? (
          <div className="mt-6 rounded-xl border border-surface-200 bg-surface-50 p-6 text-center">
            <div className="mx-auto flex h-10 w-10 items-center justify-center rounded-full bg-surface-200 text-ink-600 mb-2">
              <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
            </div>
            <h3 className="text-sm font-semibold text-ink-900">Learning Activity is Private</h3>
            <p className="mt-1 text-xs text-ink-500 max-w-sm mx-auto">
              This user has chosen to keep their learning history, enrolled courses, and achievements private.
            </p>
          </div>
        ) : (
          <>
            {/* Student Overview Stats */}
            <div className="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
              <div className="rounded-xl border border-surface-200 bg-surface-50 p-4 text-center">
                <p className="text-2xl font-bold text-ink-900">
                  {learningStats.totalEnrolledCourses}
                </p>
                <p className="mt-1 text-xs font-medium text-ink-500">Enrolled Courses</p>
              </div>

              <div className="rounded-xl border border-surface-200 bg-surface-50 p-4 text-center">
                <p className="text-2xl font-bold text-ink-900">
                  {learningStats.coursesCompleted}
                </p>
                <p className="mt-1 text-xs font-medium text-ink-500">Completed Courses</p>
              </div>

              <div className="rounded-xl border border-surface-200 bg-surface-50 p-4 text-center">
                <p className="text-2xl font-bold text-ink-900">
                  {learningStats.coursesInProgress}
                </p>
                <p className="mt-1 text-xs font-medium text-ink-500">Courses In Progress</p>
              </div>

              <div className="rounded-xl border border-surface-200 bg-surface-50 p-4 text-center">
                <p className="text-2xl font-bold text-ink-900">
                  {learningStats.lessonsCompleted}
                </p>
                <p className="mt-1 text-xs font-medium text-ink-500">Lessons Completed</p>
              </div>
            </div>

            {/* Enrolled Course Cards */}
            <div className="mt-8">
              <h3 className="text-sm font-semibold text-ink-900 mb-4">
                {isOwner ? "My Enrolled Courses" : "Completed Courses by User"}
              </h3>

              {enrolledCoursesList.length > 0 ? (
                <div className="grid gap-4 sm:grid-cols-2">
                  {enrolledCoursesList.map((c) => {
                    return (
                      <article
                        key={c.courseId}
                        className="flex flex-col justify-between overflow-hidden rounded-xl border border-surface-200 bg-white p-5 shadow-xs transition hover:border-brand-300 hover:shadow-sm"
                      >
                        <div>
                          {/* Course Thumbnail */}
                          <div className="relative mb-3 h-32 w-full overflow-hidden rounded-lg bg-surface-100">
                            {c.thumbnailUrl ? (
                              <img
                                src={c.thumbnailUrl}
                                alt={c.title}
                                className="h-full w-full object-cover"
                              />
                            ) : (
                              <div className="flex h-full w-full items-center justify-center bg-gradient-to-br from-brand-600 to-indigo-700 p-4 text-center">
                                <span className="text-sm font-bold text-white line-clamp-2">
                                  {c.title}
                                </span>
                              </div>
                            )}

                            {/* Completed Badge */}
                            {c.completed ? (
                              <span className="absolute top-2 right-2 rounded-md bg-emerald-600 px-2 py-0.5 text-xs font-semibold text-white shadow-xs">
                                ✓ Completed
                              </span>
                            ) : (
                              <span className="absolute top-2 right-2 rounded-md bg-brand-600/90 px-2 py-0.5 text-xs font-semibold text-white shadow-xs">
                                In Progress
                              </span>
                            )}
                          </div>

                          <div className="flex items-center justify-between text-xs font-medium text-ink-500">
                            <span>
                              By:{" "}
                              {c.instructorUsername ? (
                                <Link
                                  to={`/profile/${c.instructorUsername}`}
                                  className="font-semibold text-ink-700 hover:underline"
                                >
                                  {c.instructorName}
                                </Link>
                              ) : (
                                c.instructorName || "Instructor"
                              )}
                            </span>
                            {c.difficulty && (
                              <span className="rounded bg-surface-100 px-2 py-0.5 text-xs font-medium text-ink-600">
                                {c.difficulty}
                              </span>
                            )}
                          </div>

                          <h4 className="mt-1.5 font-bold text-ink-900 line-clamp-1">
                            <Link to={`/courses/${c.courseId}`} className="hover:text-brand-600 transition">
                              {c.title}
                            </Link>
                          </h4>

                          {/* Authoritative Backend Progress Bar */}
                          <div className="mt-4">
                            <div className="flex items-center justify-between text-xs font-semibold text-ink-700 mb-1">
                              <span>Progress</span>
                              <span>{Math.min(100, Math.max(0, c.progressPercent))}%</span>
                            </div>
                            <div className="h-2 w-full overflow-hidden rounded-full bg-surface-100">
                              <div
                                className={`h-full transition-all duration-300 ${
                                  c.completed ? "bg-emerald-600" : "bg-brand-600"
                                }`}
                                style={{ width: `${Math.min(100, Math.max(0, c.progressPercent))}%` }}
                              />
                            </div>
                            <div className="mt-1 flex justify-between text-xs text-ink-500">
                              <span>
                                {c.completedLessons} of {c.totalLessons} lessons completed
                              </span>
                              {c.reviewCount > 0 ? (
                                <Link
                                  to={`/courses/${c.courseId}/reviews`}
                                  className="text-amber-600 hover:underline"
                                >
                                  ★ {c.courseRating} ({c.reviewCount})
                                </Link>
                              ) : (
                                <span className="text-ink-400">No reviews</span>
                              )}
                            </div>
                          </div>
                        </div>

                        {/* Actions */}
                        <div className="mt-5 flex items-center justify-between border-t border-surface-100 pt-3">
                          <Link
                            to={`/courses/${c.courseId}/reviews`}
                            className="text-xs font-medium text-ink-600 hover:text-ink-900 hover:underline"
                          >
                            Reviews
                          </Link>

                          <div className="flex items-center gap-2">
                            <Link
                              to={`/courses/${c.courseId}`}
                              className="rounded-lg border border-surface-200 bg-white px-3 py-1.5 text-xs font-semibold text-ink-700 hover:bg-surface-50 transition"
                            >
                              Details
                            </Link>
                            <Link
                              to={`/learning/courses/${c.courseId}`}
                              className="rounded-lg bg-brand-600 px-3 py-1.5 text-xs font-semibold text-white shadow-xs hover:bg-brand-700 transition"
                            >
                              {c.completed ? "Review Course" : "Continue Learning"}
                            </Link>
                          </div>
                        </div>
                      </article>
                    );
                  })}
                </div>
              ) : (
                <div className="rounded-xl border border-dashed border-surface-200 p-8 text-center">
                  <p className="text-sm font-medium text-ink-700">
                    {isOwner
                      ? "You haven't enrolled in any courses yet."
                      : "No enrolled or completed courses to display."}
                  </p>
                  {isOwner && (
                    <div className="mt-4">
                      <Link
                        to="/courses"
                        className="inline-flex items-center justify-center rounded-lg bg-brand-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-brand-700 transition"
                      >
                        Explore Course Catalog
                      </Link>
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* 4. Skills: Declared Versus Earned */}
            <div className="mt-10 border-t border-surface-100 pt-8 space-y-8">
              {/* Subsection A: Declared Skills */}
              <div>
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-base font-bold text-ink-900">
                      Skills I Added
                    </h3>
                    <p className="text-xs text-ink-500">
                      Self-declared skills and competencies added by the user.
                    </p>
                  </div>
                  {isOwner && (
                    <Button onClick={openEditModal} variant="secondary" className="text-xs py-1 px-3">
                      + Manage Skills
                    </Button>
                  )}
                </div>

                <div className="mt-3">
                  {declaredSkillsList.length > 0 ? (
                    <div className="flex flex-wrap gap-2">
                      {declaredSkillsList.map((skill, idx) => (
                        <span
                          key={idx}
                          className="inline-flex items-center rounded-lg bg-surface-100 px-3 py-1.5 text-xs font-semibold text-ink-800 border border-surface-200"
                        >
                          {skill}
                        </span>
                      ))}
                    </div>
                  ) : (
                    <p className="text-xs italic text-ink-400">
                      {isOwner
                        ? "You haven't added any declared skills yet. Click '+ Manage Skills' to add technical competencies."
                        : "No declared skills added."}
                    </p>
                  )}
                </div>
              </div>

              {/* Subsection B: Earned Skills Through Learning */}
              <div>
                <div>
                  <h3 className="text-base font-bold text-ink-900">
                    Skills Earned Through Learning
                  </h3>
                  <p className="text-xs text-ink-500">
                    Verified competencies derived strictly from confirmed PeerGeek course completions.
                  </p>
                </div>

                <div className="mt-3">
                  {earnedSkillsList.length > 0 ? (
                    <div className="grid gap-3 sm:grid-cols-2 md:grid-cols-3">
                      {earnedSkillsList.map((skillItem, idx) => (
                        <div
                          key={idx}
                          className="flex flex-col justify-between rounded-xl border border-emerald-200 bg-emerald-50/50 p-3.5 shadow-2xs"
                        >
                          <div className="flex items-start gap-2">
                            <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-emerald-600 text-white text-xs font-bold">
                              ✓
                            </span>
                            <div>
                              <h4 className="text-sm font-bold text-emerald-950">
                                {skillItem.skillName}
                              </h4>
                              {skillItem.sourceCourses && skillItem.sourceCourses.length > 0 && (
                                <p className="mt-1 text-xs text-emerald-800/90 line-clamp-2">
                                  Verified by: {skillItem.sourceCourses.join(", ")}
                                </p>
                              )}
                            </div>
                          </div>
                          {skillItem.earnedAt && (
                            <p className="mt-2 text-[10px] text-emerald-700 text-right">
                              Earned {new Date(skillItem.earnedAt).toLocaleDateString()}
                            </p>
                          )}
                        </div>
                      ))}
                    </div>
                  ) : (
                    <div className="rounded-xl border border-dashed border-surface-200 bg-surface-50 p-6 text-center">
                      <p className="text-xs text-ink-500">
                        No earned skills yet. Complete enrolled courses to earn verified learning competencies on PeerGeek.
                      </p>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </>
        )}
      </section>

      {/* 5. Showcased Projects */}
      <section className="rounded-2xl border border-surface-200 bg-white p-6 shadow-sm sm:p-8">
        <div className="flex items-center justify-between border-b border-surface-100 pb-4">
          <div>
            <h2 className="text-xl font-bold tracking-tight text-ink-900">Projects</h2>
            <p className="text-xs text-ink-500">
              Open source repositories, tools, and portfolios.
            </p>
          </div>
          {isOwner && (
            <Button onClick={openAddProject} variant="secondary">
              + Add Project
            </Button>
          )}
        </div>

        {profile.projects && profile.projects.length > 0 ? (
          <div className="mt-6 grid gap-4 sm:grid-cols-2">
            {profile.projects.map((proj) => (
              <div
                className="flex flex-col justify-between rounded-xl border border-surface-200 p-4 transition hover:border-surface-300 shadow-2xs"
                key={proj.id}
              >
                <div>
                  <div className="flex items-start justify-between gap-2">
                    <h3 className="font-semibold text-ink-900">{proj.title}</h3>
                    {isOwner && (
                      <div className="flex items-center gap-1.5 shrink-0">
                        <button
                          className="text-xs text-ink-500 hover:text-brand-600"
                          onClick={() => openEditProject(proj)}
                          type="button"
                        >
                          Edit
                        </button>
                        <span className="text-ink-300">|</span>
                        <button
                          className="text-xs text-danger-600 hover:text-danger-800"
                          onClick={() => handleDeleteProject(proj.id)}
                          type="button"
                        >
                          Delete
                        </button>
                      </div>
                    )}
                  </div>
                  {proj.description && (
                    <p className="mt-1.5 text-xs text-ink-700 whitespace-pre-wrap leading-relaxed">
                      {proj.description}
                    </p>
                  )}
                  {proj.technologies && (
                    <div className="mt-3 flex flex-wrap gap-1">
                      {proj.technologies.split(",").map((tech, i) => (
                        <span
                          className="rounded bg-surface-100 px-2 py-0.5 text-xs font-medium text-ink-600"
                          key={i}
                        >
                          {tech.trim()}
                        </span>
                      ))}
                    </div>
                  )}
                </div>

                {(proj.projectUrl || proj.repoUrl) && (
                  <div className="mt-4 flex items-center gap-3 border-t border-surface-100 pt-3 text-xs">
                    {proj.projectUrl && (
                      <a
                        className="font-medium text-brand-600 hover:underline"
                        href={proj.projectUrl}
                        rel="noreferrer noopener"
                        target="_blank"
                      >
                        Live Demo ↗
                      </a>
                    )}
                    {proj.repoUrl && (
                      <a
                        className="font-medium text-ink-700 hover:underline"
                        href={proj.repoUrl}
                        rel="noreferrer noopener"
                        target="_blank"
                      >
                        Repository ↗
                      </a>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        ) : (
          <p className="mt-4 text-xs italic text-ink-400">
            {isOwner
              ? "Showcase your software projects and portfolios here! Click '+ Add Project' to begin."
              : "No projects showcased yet."}
          </p>
        )}
      </section>

      {/* 6. Education Section */}
      <section className="rounded-2xl border border-surface-200 bg-white p-6 shadow-sm sm:p-8">
        <div className="flex items-center justify-between border-b border-surface-100 pb-4">
          <div>
            <h2 className="text-xl font-bold tracking-tight text-ink-900">Education</h2>
            <p className="text-xs text-ink-500">
              Academic institutions, degrees, and study fields.
            </p>
          </div>
          {isOwner && (
            <Button onClick={openAddEducation} variant="secondary">
              + Add Education
            </Button>
          )}
        </div>

        {profile.educations && profile.educations.length > 0 ? (
          <div className="mt-4 divide-y divide-surface-100">
            {profile.educations.map((edu) => (
              <div className="flex items-start justify-between py-3.5 first:pt-2 last:pb-0" key={edu.id}>
                <div>
                  <h3 className="font-semibold text-ink-900 text-sm">{edu.institution}</h3>
                  {(edu.degree || edu.fieldOfStudy) && (
                    <p className="text-xs text-ink-700">
                      {[edu.degree, edu.fieldOfStudy].filter(Boolean).join(" in ")}
                    </p>
                  )}
                  {(edu.startYear || edu.endYear) && (
                    <p className="mt-0.5 text-xs text-ink-500">
                      {edu.startYear ? edu.startYear : "—"} – {edu.endYear ? edu.endYear : "Present"}
                    </p>
                  )}
                </div>
                {isOwner && (
                  <div className="flex items-center gap-1.5 shrink-0">
                    <button
                      className="text-xs text-ink-500 hover:text-brand-600"
                      onClick={() => openEditEducation(edu)}
                      type="button"
                    >
                      Edit
                    </button>
                    <span className="text-ink-300">|</span>
                    <button
                      className="text-xs text-danger-600 hover:text-danger-800"
                      onClick={() => handleDeleteEducation(edu.id)}
                      type="button"
                    >
                      Delete
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        ) : (
          <p className="mt-4 text-xs italic text-ink-400">
            {isOwner
              ? "Add your degrees, universities, or academic qualifications."
              : "No education details provided or hidden by privacy settings."}
          </p>
        )}
      </section>

      {/* 7. Platform Achievements Section */}
      {profile.achievements && profile.achievements.length > 0 && (
        <section className="rounded-2xl border border-surface-200 bg-white p-6 shadow-sm sm:p-8">
          <div className="border-b border-surface-100 pb-4">
            <h2 className="text-xl font-bold tracking-tight text-ink-900">Achievements & Certificates</h2>
            <p className="text-xs text-ink-500">
              Verified course completion certificates earned on PeerGeek.
            </p>
          </div>
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            {profile.achievements.map((ach, i) => (
              <div className="flex items-center gap-3 rounded-xl border border-surface-200 bg-surface-50 p-3.5 shadow-2xs" key={i}>
                <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-amber-100 text-amber-700 font-bold text-base">
                  ✓
                </div>
                <div>
                  <h4 className="font-semibold text-ink-900 text-sm line-clamp-1">{ach.courseTitle}</h4>
                  <p className="text-xs text-ink-500 mt-0.5">
                    Completed on {new Date(ach.completedAt).toLocaleDateString()}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </section>
      )}

      {/* Edit Profile Modal */}
      {editModalOpen && (
        <Dialog
          onOpenChange={setEditModalOpen}
          open={editModalOpen}
          title="Edit Profile"
        >
          <form className="grid gap-4 max-h-[75vh] overflow-y-auto pr-1" onSubmit={handleSaveProfile}>
            {editFormErrors.root && (
              <p className="text-sm text-danger-600">{editFormErrors.root}</p>
            )}

            <div>
              <label className="block text-xs font-semibold text-ink-700" htmlFor="edit-displayName">
                Display Name *
              </label>
              <input
                className="mt-1 w-full rounded-md border border-surface-200 px-3 py-2 text-sm text-ink-900"
                id="edit-displayName"
                maxLength={100}
                onChange={(e) => setEditForm((f) => ({ ...f, displayName: e.target.value }))}
                required
                value={editForm.displayName}
              />
              {editFormErrors.displayName && (
                <p className="mt-1 text-xs text-danger-600">{editFormErrors.displayName}</p>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-ink-700" htmlFor="edit-username">
                Username (@) *
              </label>
              <input
                className="mt-1 w-full rounded-md border border-surface-200 px-3 py-2 text-sm text-ink-900"
                id="edit-username"
                maxLength={30}
                onChange={(e) => setEditForm((f) => ({ ...f, username: e.target.value }))}
                required
                value={editForm.username}
              />
              <p className="mt-0.5 text-xs text-ink-500">
                3-30 chars, lowercase letters, numbers, underscores. Must start with letter.
              </p>
              {editFormErrors.username && (
                <p className="mt-1 text-xs text-danger-600">{editFormErrors.username}</p>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-ink-700" htmlFor="edit-headline">
                Headline
              </label>
              <input
                className="mt-1 w-full rounded-md border border-surface-200 px-3 py-2 text-sm text-ink-900"
                id="edit-headline"
                maxLength={160}
                onChange={(e) => setEditForm((f) => ({ ...f, headline: e.target.value }))}
                placeholder="e.g. Java Backend Engineer | CS Student"
                value={editForm.headline}
              />
              {editFormErrors.headline && (
                <p className="mt-1 text-xs text-danger-600">{editFormErrors.headline}</p>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-ink-700" htmlFor="edit-bio">
                Bio
              </label>
              <textarea
                className="mt-1 min-h-24 w-full rounded-md border border-surface-200 px-3 py-2 text-sm text-ink-900"
                id="edit-bio"
                maxLength={2000}
                onChange={(e) => setEditForm((f) => ({ ...f, bio: e.target.value }))}
                placeholder="Share your interests, learning background, or software philosophy..."
                value={editForm.bio}
              />
              {editFormErrors.bio && (
                <p className="mt-1 text-xs text-danger-600">{editFormErrors.bio}</p>
              )}
            </div>

            {/* Declared Skills Chip Editor */}
            <div>
              <label className="block text-xs font-semibold text-ink-700">
                Skills I Added (Declared Skills)
              </label>
              <div className="mt-1.5 flex flex-wrap items-center gap-1.5 rounded-lg border border-surface-200 bg-white p-2 min-h-12">
                {editForm.skills.map((skill, idx) => (
                  <span
                    key={idx}
                    className="inline-flex items-center gap-1 rounded-md bg-brand-50 px-2.5 py-1 text-xs font-medium text-brand-700 border border-brand-100"
                  >
                    <span>{skill}</span>
                    <button
                      type="button"
                      onClick={() => handleRemoveDeclaredSkill(idx)}
                      className="ml-1 text-brand-400 hover:text-brand-800 font-bold"
                      aria-label={`Remove skill ${skill}`}
                    >
                      ×
                    </button>
                  </span>
                ))}
                <div className="flex items-center gap-1 flex-1 min-w-[140px]">
                  <input
                    type="text"
                    value={newSkillInput}
                    onChange={(e) => setNewSkillInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        e.preventDefault();
                        handleAddDeclaredSkill();
                      }
                    }}
                    placeholder="Type skill & press Enter..."
                    className="w-full text-xs text-ink-900 border-none outline-none focus:ring-0 px-1 py-1"
                  />
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={handleAddDeclaredSkill}
                    className="text-xs py-1 px-2 shrink-0"
                  >
                    + Add
                  </Button>
                </div>
              </div>
              {skillInputError && (
                <p className="mt-1 text-xs text-danger-600">{skillInputError}</p>
              )}
              {editFormErrors.skills && (
                <p className="mt-1 text-xs text-danger-600">{editFormErrors.skills}</p>
              )}
            </div>

            <div>
              <label className="block text-xs font-semibold text-ink-700" htmlFor="edit-interests">
                Interests (comma separated)
              </label>
              <input
                className="mt-1 w-full rounded-md border border-surface-200 px-3 py-2 text-sm text-ink-900"
                id="edit-interests"
                onChange={(e) => setEditForm((f) => ({ ...f, interests: e.target.value }))}
                placeholder="Distributed Systems, Cloud Architecture, AI"
                value={editForm.interests}
              />
            </div>

            {isInstructor && (
              <div>
                <label className="block text-xs font-semibold text-ink-700" htmlFor="edit-teachingExpertise">
                  Teaching Expertise
                </label>
                <textarea
                  className="mt-1 min-h-20 w-full rounded-md border border-surface-200 px-3 py-2 text-sm text-ink-900"
                  id="edit-teachingExpertise"
                  maxLength={1000}
                  onChange={(e) => setEditForm((f) => ({ ...f, teachingExpertise: e.target.value }))}
                  placeholder="Courses, domains, and technical subjects you teach..."
                  value={editForm.teachingExpertise}
                />
              </div>
            )}

            {/* Social Links */}
            <div className="border-t border-surface-100 pt-3">
              <h4 className="text-xs font-semibold uppercase tracking-wider text-ink-500 mb-2">
                Social & Professional Links
              </h4>
              <div className="grid gap-2">
                <input
                  className="w-full rounded-md border border-surface-200 px-3 py-1.5 text-xs text-ink-900"
                  onChange={(e) => setEditForm((f) => ({ ...f, githubUrl: e.target.value }))}
                  placeholder="https://github.com/your-username"
                  value={editForm.githubUrl}
                />
                <input
                  className="w-full rounded-md border border-surface-200 px-3 py-1.5 text-xs text-ink-900"
                  onChange={(e) => setEditForm((f) => ({ ...f, linkedinUrl: e.target.value }))}
                  placeholder="https://linkedin.com/in/your-profile"
                  value={editForm.linkedinUrl}
                />
                <input
                  className="w-full rounded-md border border-surface-200 px-3 py-1.5 text-xs text-ink-900"
                  onChange={(e) => setEditForm((f) => ({ ...f, portfolioUrl: e.target.value }))}
                  placeholder="https://yourportfolio.dev"
                  value={editForm.portfolioUrl}
                />
                <input
                  className="w-full rounded-md border border-surface-200 px-3 py-1.5 text-xs text-ink-900"
                  onChange={(e) => setEditForm((f) => ({ ...f, websiteUrl: e.target.value }))}
                  placeholder="https://yourwebsite.com"
                  value={editForm.websiteUrl}
                />
              </div>
            </div>

            {/* Privacy Controls */}
            <div className="border-t border-surface-100 pt-3">
              <h4 className="text-xs font-semibold uppercase tracking-wider text-ink-500 mb-2">
                Privacy Settings
              </h4>
              <div className="space-y-2">
                <label className="flex items-center gap-2 text-xs text-ink-700 cursor-pointer">
                  <input
                    checked={editForm.showEducation}
                    className="rounded border-surface-300 text-brand-600 focus:ring-brand-500"
                    onChange={(e) => setEditForm((f) => ({ ...f, showEducation: e.target.checked }))}
                    type="checkbox"
                  />
                  <span>Show education on public profile</span>
                </label>
                <label className="flex items-center gap-2 text-xs text-ink-700 cursor-pointer">
                  <input
                    checked={editForm.showSocialLinks}
                    className="rounded border-surface-300 text-brand-600 focus:ring-brand-500"
                    onChange={(e) => setEditForm((f) => ({ ...f, showSocialLinks: e.target.checked }))}
                    type="checkbox"
                  />
                  <span>Show social and professional links on public profile</span>
                </label>
                <label className="flex items-center gap-2 text-xs text-ink-700 cursor-pointer">
                  <input
                    checked={editForm.showLearningHistory}
                    className="rounded border-surface-300 text-brand-600 focus:ring-brand-500"
                    onChange={(e) => setEditForm((f) => ({ ...f, showLearningHistory: e.target.checked }))}
                    type="checkbox"
                  />
                  <span>Show completed courses and platform achievements on public profile</span>
                </label>
              </div>
            </div>

            <div className="mt-4 flex justify-end gap-3 border-t border-surface-100 pt-4">
              <Button onClick={() => setEditModalOpen(false)} variant="secondary">
                Cancel
              </Button>
              <Button disabled={savingProfile} type="submit" variant="primary">
                {savingProfile ? "Saving..." : "Save Changes"}
              </Button>
            </div>
          </form>
        </Dialog>
      )}

      {/* Education Modal */}
      {eduModalOpen && (
        <Dialog
          onOpenChange={setEduModalOpen}
          open={eduModalOpen}
          title={editingEdu ? "Edit Education" : "Add Education"}
        >
          <form className="grid gap-3" onSubmit={handleSaveEducation}>
            {eduErrors.root && <p className="text-xs text-danger-600">{eduErrors.root}</p>}
            <div>
              <label className="block text-xs font-semibold text-ink-700" htmlFor="edu-inst">
                Institution / University *
              </label>
              <input
                className="mt-1 w-full rounded-md border border-surface-200 px-3 py-1.5 text-sm text-ink-900"
                id="edu-inst"
                maxLength={200}
                onChange={(e) => setEduForm((f) => ({ ...f, institution: e.target.value }))}
                required
                value={eduForm.institution}
              />
              {eduErrors.institution && (
                <p className="mt-1 text-xs text-danger-600">{eduErrors.institution}</p>
              )}
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-ink-700" htmlFor="edu-degree">
                  Degree
                </label>
                <input
                  className="mt-1 w-full rounded-md border border-surface-200 px-3 py-1.5 text-sm text-ink-900"
                  id="edu-degree"
                  maxLength={100}
                  onChange={(e) => setEduForm((f) => ({ ...f, degree: e.target.value }))}
                  placeholder="e.g. B.S., M.S., Ph.D."
                  value={eduForm.degree}
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-ink-700" htmlFor="edu-field">
                  Field of Study
                </label>
                <input
                  className="mt-1 w-full rounded-md border border-surface-200 px-3 py-1.5 text-sm text-ink-900"
                  id="edu-field"
                  maxLength={100}
                  onChange={(e) => setEduForm((f) => ({ ...f, fieldOfStudy: e.target.value }))}
                  placeholder="e.g. Computer Science"
                  value={eduForm.fieldOfStudy}
                />
              </div>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-semibold text-ink-700" htmlFor="edu-start">
                  Start Year
                </label>
                <input
                  className="mt-1 w-full rounded-md border border-surface-200 px-3 py-1.5 text-sm text-ink-900"
                  id="edu-start"
                  max={2100}
                  min={1900}
                  onChange={(e) => setEduForm((f) => ({ ...f, startYear: e.target.value }))}
                  placeholder="2020"
                  type="number"
                  value={eduForm.startYear}
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-ink-700" htmlFor="edu-end">
                  End Year
                </label>
                <input
                  className="mt-1 w-full rounded-md border border-surface-200 px-3 py-1.5 text-sm text-ink-900"
                  id="edu-end"
                  max={2100}
                  min={1900}
                  onChange={(e) => setEduForm((f) => ({ ...f, endYear: e.target.value }))}
                  placeholder="2024"
                  type="number"
                  value={eduForm.endYear}
                />
              </div>
            </div>

            <div className="mt-4 flex justify-end gap-2 border-t border-surface-100 pt-3">
              <Button onClick={() => setEduModalOpen(false)} variant="secondary">
                Cancel
              </Button>
              <Button disabled={savingEdu} type="submit" variant="primary">
                {savingEdu ? "Saving..." : "Save Education"}
              </Button>
            </div>
          </form>
        </Dialog>
      )}

      {/* Project Modal */}
      {projModalOpen && (
        <Dialog
          onOpenChange={setProjModalOpen}
          open={projModalOpen}
          title={editingProj ? "Edit Project" : "Add Project"}
        >
          <form className="grid gap-3" onSubmit={handleSaveProject}>
            {projErrors.root && <p className="text-xs text-danger-600">{projErrors.root}</p>}
            <div>
              <label className="block text-xs font-semibold text-ink-700" htmlFor="proj-title">
                Project Title *
              </label>
              <input
                className="mt-1 w-full rounded-md border border-surface-200 px-3 py-1.5 text-sm text-ink-900"
                id="proj-title"
                maxLength={100}
                onChange={(e) => setProjForm((f) => ({ ...f, title: e.target.value }))}
                required
                value={projForm.title}
              />
              {projErrors.title && <p className="mt-1 text-xs text-danger-600">{projErrors.title}</p>}
            </div>

            <div>
              <label className="block text-xs font-semibold text-ink-700" htmlFor="proj-desc">
                Description
              </label>
              <textarea
                className="mt-1 min-h-20 w-full rounded-md border border-surface-200 px-3 py-1.5 text-sm text-ink-900"
                id="proj-desc"
                maxLength={1000}
                onChange={(e) => setProjForm((f) => ({ ...f, description: e.target.value }))}
                placeholder="What did you build and why?"
                value={projForm.description}
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-ink-700" htmlFor="proj-tech">
                Technologies (comma separated)
              </label>
              <input
                className="mt-1 w-full rounded-md border border-surface-200 px-3 py-1.5 text-sm text-ink-900"
                id="proj-tech"
                maxLength={255}
                onChange={(e) => setProjForm((f) => ({ ...f, technologies: e.target.value }))}
                placeholder="React, Spring Boot, MySQL"
                value={projForm.technologies}
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-ink-700" htmlFor="proj-url">
                Live Demo URL
              </label>
              <input
                className="mt-1 w-full rounded-md border border-surface-200 px-3 py-1.5 text-sm text-ink-900"
                id="proj-url"
                onChange={(e) => setProjForm((f) => ({ ...f, projectUrl: e.target.value }))}
                placeholder="https://myproject.com"
                value={projForm.projectUrl}
              />
            </div>

            <div>
              <label className="block text-xs font-semibold text-ink-700" htmlFor="proj-repo">
                Repository URL
              </label>
              <input
                className="mt-1 w-full rounded-md border border-surface-200 px-3 py-1.5 text-sm text-ink-900"
                id="proj-repo"
                onChange={(e) => setProjForm((f) => ({ ...f, repoUrl: e.target.value }))}
                placeholder="https://github.com/myusername/myrepo"
                value={projForm.repoUrl}
              />
            </div>

            <div className="mt-4 flex justify-end gap-2 border-t border-surface-100 pt-3">
              <Button onClick={() => setProjModalOpen(false)} variant="secondary">
                Cancel
              </Button>
              <Button disabled={savingProj} type="submit" variant="primary">
                {savingProj ? "Saving..." : "Save Project"}
              </Button>
            </div>
          </form>
        </Dialog>
      )}
    </div>
  );
}
