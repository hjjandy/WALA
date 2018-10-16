This is NOT the main source repository for WALA.  For more details on WALA, see <a
href="http://wala.sourceforge.net">the WALA home page</a>.


#### How to merge from upstream WALA:

##### 0. git remote add upstream https://github.com/wala/WALA.git

##### 1. git fetch upstream

##### 2. git merge upstream/master

##### 3. resolve conflicts (rm unwanted directories/files, handle changes in existing files)

##### 4. git push origin


**Note[from main WALA repo]:** historically, WALA has used Maven as its build system.
However, this WALA branch can also use Gradle as an alternative to
Maven.  See [the Gradle-specific README](README-Gradle.md) for more
instructions and helpful tips.
