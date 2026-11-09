package com.peergeek.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("peergeek.course")
public class CourseSettings {
	private int minimumTitleLength = 3;
	private int maximumTitleLength = 120;
	private int maximumDescriptionLength = 5000;
	private int maximumTopics = 10;
	private int minimumTopicLength = 2;
	private int maximumTopicLength = 40;
	private int maximumModuleTitleLength = 120;
	private int maximumModuleDescriptionLength = 1000;
	private int maximumModulesPerCourse = 200;
	private int maximumLessonTitleLength = 150;
	private int maximumLessonSummaryLength = 500;
	private int maximumLessonsPerModule = 500;
	private int maximumContentJsonBytes = 2 * 1024 * 1024;
	private int maximumContentDepth = 20;

	public int getMinimumTitleLength() { return minimumTitleLength; }
	public void setMinimumTitleLength(int value) { minimumTitleLength = value; }
	public int getMaximumTitleLength() { return maximumTitleLength; }
	public void setMaximumTitleLength(int value) { maximumTitleLength = value; }
	public int getMaximumDescriptionLength() { return maximumDescriptionLength; }
	public void setMaximumDescriptionLength(int value) { maximumDescriptionLength = value; }
	public int getMaximumTopics() { return maximumTopics; }
	public void setMaximumTopics(int value) { maximumTopics = value; }
	public int getMinimumTopicLength() { return minimumTopicLength; }
	public void setMinimumTopicLength(int value) { minimumTopicLength = value; }
	public int getMaximumTopicLength() { return maximumTopicLength; }
	public void setMaximumTopicLength(int value) { maximumTopicLength = value; }
	public int getMaximumModuleTitleLength() { return maximumModuleTitleLength; }
	public void setMaximumModuleTitleLength(int value) { maximumModuleTitleLength = value; }
	public int getMaximumModuleDescriptionLength() { return maximumModuleDescriptionLength; }
	public void setMaximumModuleDescriptionLength(int value) { maximumModuleDescriptionLength = value; }
	public int getMaximumModulesPerCourse() { return maximumModulesPerCourse; }
	public void setMaximumModulesPerCourse(int value) { maximumModulesPerCourse = value; }
	public int getMaximumLessonTitleLength() { return maximumLessonTitleLength; }
	public void setMaximumLessonTitleLength(int value) { maximumLessonTitleLength = value; }
	public int getMaximumLessonSummaryLength() { return maximumLessonSummaryLength; }
	public void setMaximumLessonSummaryLength(int value) { maximumLessonSummaryLength = value; }
	public int getMaximumLessonsPerModule() { return maximumLessonsPerModule; }
	public void setMaximumLessonsPerModule(int value) { maximumLessonsPerModule = value; }
	public int getMaximumContentJsonBytes() { return maximumContentJsonBytes; }
	public void setMaximumContentJsonBytes(int value) { maximumContentJsonBytes = value; }
	public int getMaximumContentDepth() { return maximumContentDepth; }
	public void setMaximumContentDepth(int value) { maximumContentDepth = value; }
}
