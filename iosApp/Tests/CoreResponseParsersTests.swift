import Foundation

@main
enum CoreResponseParsersTests {
    static func main() {
        let detail = AppDetailParser.parse(
            """
            {
              "data": {
                "title": "示例应用",
                "packageName": "app.example",
                "apkRomIcon": "http://image.coolapk.com/icon/a.png",
                "developername": "开发者",
                "apkversionname": "2.1",
                "apksize": "12 MB",
                "lastupdate": "1720000000",
                "score": "8.7",
                "downCountFormatted": "20万",
                "description": "",
                "introduce": "功能介绍",
                "changeLog": "更新内容",
                "screenList": [
                  "//image.coolapk.com/screen/a.jpg",
                  {"url": "screen/b.jpg"}
                ]
              }
            }
            """
        )
        precondition(detail?.name == "示例应用")
        precondition(detail?.packageName == "app.example")
        precondition(detail?.iconURL?.absoluteString == "https://image.coolapk.com/icon/a.png")
        precondition(detail?.developer == "开发者")
        precondition(detail?.version == "2.1")
        precondition(detail?.size == "12 MB")
        precondition(detail?.updatedAt == "1720000000")
        precondition(detail?.overview == "功能介绍")
        precondition(detail?.changeLog == "更新内容")
        precondition(detail?.screenshotURLs.map(\.absoluteString) == [
            "https://image.coolapk.com/screen/a.jpg",
            "https://image.coolapk.com/screen/b.jpg",
        ])
        precondition(AppDetailParser.parse(#"{"data":{"packageName":"app.example"}}"#) == nil)

        precondition(NotificationCountParser.parse(#"{"code":200,"data":5}"#) == 5)
        precondition(NotificationCountParser.parse(#"{"data":{"badge_v18":3}}"#) == 3)
        precondition(NotificationCountParser.parse(#"{"data":{"commentme":2,"atme":1}}"#) == 3)
        print("CoreResponseParsersTests passed")
    }
}
