def getVideoId(videoUrl){
    def videoId = ""
    if (videoUrl.startsWith("https://www.youtube.com/watch?v")){
        videoId = videoUrl.split("=")[1]
    } else if (videoUrl.startsWith("https://youtu.be/")) {
        videoId = videoUrl.split("/")[3]
    }
    return videoId
}

def getVideoIds(videoUrls){
    def videoIds = []
    for( url in videoUrls){
        videoIds << getVideoId(url)
    }
    return videoIds
}

def getVideoCategory(){
    withCredentials([
        string(credentialsId: 'YOUTUBE_API_KEY', variable: 'YOUTUBE_API_KEY')
    ]) {
        def params = []
        params.add("part=snippet")
        params.add("regionCode=jp")
        params.add("key=${YOUTUBE_API_KEY}")
        urlParams = params.join('&')
        withEnv([
            "URL=https://www.googleapis.com/youtube/v3/videoCategories?${urlParams}",
        ]) {
            def urlJsonTxt = sh( returnStdout: true, script: 'curl --fail ${URL}')
            def urlJson = readJSON text: urlJsonTxt
            return urlJson
        }            
    }
}

def getVideoInfo(videoId){
    withCredentials([
        string(credentialsId: 'YOUTUBE_API_KEY', variable: 'YOUTUBE_API_KEY')
    ]) {
        def params = []
        params.add("part=snippet")
        params.add("id=${videoId}")
        params.add("key=${YOUTUBE_API_KEY}")
        urlParams = params.join('&')
        withEnv([
            "URL=https://www.googleapis.com/youtube/v3/videos?${urlParams}",
        ]) {
            def urlJsonTxt = sh( returnStdout: true, script: 'curl --fail ${URL}')
            def urlJson = readJSON text: urlJsonTxt
            return urlJson
        }            
    }
}

def getVideoInfoList(videoIds){
    def videoInfoList =[]
    for(videoId in videoIds){
        videoInfoList << getVideoInfo(videoId)       
    }
    return videoInfoList
}

def getSearchVideoInfo(searchWord,part = "snippet", type = "video", maxResults = 2, order = "viewCount" ,publishedDaysAgo = 7){
    withCredentials([
        string(credentialsId: 'YOUTUBE_API_KEY', variable: 'YOUTUBE_API_KEY')
    ]) {
        def params = []
        params.add("part=${part}")
        params.add("type=${type}")
        def apiQuestion = "${searchWord}".replaceAll(' ','%20').replaceAll('　','%20')
        params.add("q=${apiQuestion}")
        params.add("maxResults=${maxResults}")
        params.add("order=${order}")
        def daysAgo = (new Date() - "${publishedDaysAgo}".toInteger()).format("yyyy-MM-dd'T'HH:mm:ss'Z'")
        params.add("publishedAfter=${daysAgo}")
        params.add("key=${YOUTUBE_API_KEY}")
        urlParams = params.join('&')
        withEnv([
            "URL=https://www.googleapis.com/youtube/v3/search?${urlParams}",
        ]) {
            def urlJsonTxt = sh( returnStdout: true, script: 'curl --fail ${URL}')
            def urlJson = readJSON text: urlJsonTxt
            return urlJson
        }            
    }
}

def getSearchVideoInfoList(searchWords){
    def searchVideoInfoList =[]
    for(searchWord in searchWords){
        searchVideoInfoList << getSearchVideoInfo(searchWord)
    }
    return searchVideoInfoList
}

def getSearchVideoInfoList(searchWords,publishedDaysAgo){
    def searchVideoInfoList =[]
    for(searchWord in searchWords){
        searchVideoInfoList << getSearchVideoInfo(searchWord,"snippet","video",2,"viewCount",publishedDaysAgo)
    }
    return searchVideoInfoList
}

def getThumbnailsUrl(videoId,size = "standard"){
    def videoInfo = getVideoInfo(videoId)
    def thumbnailsUrl = videoInfo.items[0].snippet.thumbnails."${size}".url
    return thumbnailsUrl
}

def uploadVideo(title,videoPath,credentialFileId = 'youtube_upload_credential',secretFileId = 'youtube_upload_client_secret'){
    def videoId = ""
    withCredentials([
        file(credentialsId: credentialFileId, variable: 'CREDENTIAL_FILE'),
        file(credentialsId: secretFileId, variable: 'CLIENT_SECRET_FILE')
    ]) {
        sh "wget https://github.com/tokland/youtube-upload/archive/master.zip"
        unzip dir: '', glob: '', zipFile: 'master.zip'
        withDockerContainer(args: '-u 0', image: 'python:2.7-alpine3.6') {
            sh "pip install --upgrade httplib2 oauth2client rsa uritemplate google-api-python-client==1.7.4 progressbar2"
            sh "cd youtube-upload-master ; python setup.py install"
            withEnv(["TITLE=${title}"]) {
                def params = []
                params.add("--title=\'${TITLE}\'")
                params.add("--default-language=ja")
                params.add("--default-audio-language=ja")
                params.add("--credentials-file=${CREDENTIAL_FILE}")
                params.add("--client-secrets=${CLIENT_SECRET_FILE}")
                def cmd = "youtube-upload "+ params.join(" ") + " ${videoPath}"
                videoId = sh( returnStdout: true, script: cmd).trim()
            }
        }
    }  
    return videoId
}

def updateVideo(videoId,title,descriptionFile,categoryId,tags,credentialFileId = 'youtube_upload_credential',secretFileId = 'youtube_upload_client_secret'){
    def result = ""
    withCredentials([
        file(credentialsId: credentialFileId, variable: 'CREDENTIAL_FILE'),
        file(credentialsId: secretFileId, variable: 'CLIENT_SECRET_FILE')
    ]) {
        def script = libraryResource 'youtube/video_update.py'
        writeFile file: "video_update.py",text: script
        withDockerContainer(args: '-u 0', image: 'python:2.7-alpine3.6') {
            sh "pip install --upgrade httplib2 oauth2client rsa uritemplate google-api-python-client progressbar2"
            withEnv(["TITLE=${title}"]) {
                def params = []
                params.add("--video-id=${videoId}")
                params.add("--title=\'${TITLE}\'")
                params.add("--description-file=${descriptionFile}")
                params.add("--category-id=${categoryId}")
                params.add("--tags=${tags}")
                params.add("--credentials-file=${CREDENTIAL_FILE}")
                params.add("--client-secrets=${CLIENT_SECRET_FILE}")
                def cmd = "python video_update.py "+ params.join(" ")
                result = sh( returnStdout: true, script: cmd).trim()
                echo result
            }
        }
    }   
    return result
}

def updateThumbnails(videoId,thumbnailsImage,credentialFileId = 'youtube_upload_credential',secretFileId = 'youtube_upload_client_secret'){
    def result = ""
    withCredentials([
        file(credentialsId: "${credentialFileId}", variable: 'CREDENTIAL_FILE'),
        file(credentialsId: "${secretFileId}", variable: 'CLIENT_SECRET_FILE')
    ]) {
        def script = libraryResource 'youtube/thumbnails_update.py'
        writeFile file: "thumbnails_update.py",text: script
        withDockerContainer(args: '-u 0', image: 'python:2.7-alpine3.6') {
            sh "pip install --upgrade httplib2 oauth2client rsa uritemplate google-api-python-client progressbar2"
            def params = []
            params.add("--video-id=${videoId}")
            params.add("--file=${thumbnailsImage}")
            params.add("--credentials-file=${CREDENTIAL_FILE}")
            params.add("--client-secrets=${CLIENT_SECRET_FILE}")
            def cmd = "python thumbnails_update.py "+ params.join(" ")
            result = sh( returnStdout: true, script: cmd).trim()
            echo result
        }
    }   
    return result
}


def insertTopVideoComment(channelId,videoId,comment,credentialFileId = 'youtube_upload_credential',secretFileId = 'youtube_upload_client_secret'){
    def result = ""
    withCredentials([
        file(credentialsId: "${credentialFileId}", variable: 'CREDENTIAL_FILE'),
        file(credentialsId: "${secretFileId}", variable: 'CLIENT_SECRET_FILE')
    ]) {
        def script = libraryResource 'youtube/comment_threads.py'
        writeFile file: "comment_threads.py",text: script
        sh "curl --fail -o youtube-v3-discoverydocument.json https://www.googleapis.com/discovery/v1/apis/youtube/v3/rest"
        withDockerContainer(args: '-u 0', image: 'python:2.7-alpine3.6') {
            sh "pip install --upgrade httplib2 oauth2client rsa uritemplate google-api-python-client progressbar2"
            def params = []
            params.add("--channelid=${channelId}")
            params.add("--video-id=${videoId}")
            params.add("--text=\'${comment}\'")
            params.add("--credentials-file=${CREDENTIAL_FILE}")
            params.add("--client-secrets=${CLIENT_SECRET_FILE}")
            def cmd = "python comment_threads.py "+ params.join(" ")
            result = sh( returnStdout: true, script: cmd).trim()
            echo result
        }
    }   
    return result
}
