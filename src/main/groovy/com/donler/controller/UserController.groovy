package com.donler.controller

import com.donler.config.AppConfig
import com.donler.exception.BadRequestException
import com.donler.exception.DatabaseDuplicateException
import com.donler.exception.NotFoundException
import com.donler.model.Constants
import com.donler.model.SimpleCompanyModel
import com.donler.model.SimpleTeamModel
import com.donler.model.SimpleUserModel
import com.donler.model.persistent.user.ColleagueItem
import com.donler.model.persistent.user.Token
import com.donler.model.persistent.user.User
import com.donler.model.request.team.TeamInviteMembersRequestBody
import com.donler.model.request.user.UserAddMemoRequestModel
import com.donler.model.request.user.UserLoginRequestModel
import com.donler.model.request.user.UserProfileModifyRequestModel
import com.donler.model.request.user.UserRegisterRequestModel
import com.donler.model.response.ColleagueTrendItem
import com.donler.model.response.ResponseMsg
import com.donler.model.response.User as ResUser
import com.donler.repository.company.CompanyRepository
import com.donler.repository.team.TeamRepository
import com.donler.repository.trend.*
import com.donler.repository.user.ColleagueItemRepository
import com.donler.repository.user.TokenRepository
import com.donler.repository.user.UserRepository
import com.donler.service.*
import io.swagger.annotations.Api
import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import net.sf.json.JSON
import net.sf.json.util.JSONBuilder
import org.json.simple.JSONObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.convert.converter.Converter
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

import javax.servlet.http.HttpServletRequest
import javax.validation.Valid

/**
 * Created by jason on 5/27/16.
 */
@RestController
@RequestMapping("/user")
@Api(value = "user", tags = ["用户"], consumes = "application/json", produces = "application/json")
class UserController {

    @Autowired
    UserRepository userRepository

    @Autowired
    TokenService tokenService

    @Autowired
    AppConfig appConfig

    @Autowired
    TokenRepository tokenRepository

    @Autowired
    CompanyRepository companyRepository

    @Autowired
    OSSService ossService

    @Autowired
    TopicRepository topicRepository

    @Autowired
    VoteRepository voteRepository

    @Autowired
    TrendController trendController

    @Autowired
    ActivityRepository activityRepository

    @Autowired
    ColleagueItemRepository colleagueItemRepository

    @Autowired
    TeamRepository teamRepository

    @Autowired
    TrendItemRepository trendItemRepository

    @Autowired
    ShowtimeRepository showtimeRepository

    @Autowired
    EasemobController easemobController

    @Autowired
    YunpianService yunpianService



    @ApiOperation(value = "登录", notes = "根据传入的用户名/邮箱/手机号码和密码来进行登录", response = Token.class)
    @RequestMapping(path = "/login", method = RequestMethod.POST)
    def login(@Valid @RequestBody UserLoginRequestModel body) {

        def user = userRepository.findByUsernameOrPhoneOrEmail(body?.loginInfo, body?.loginInfo, body?.loginInfo)
        if (user?.password != MD5Util.md5Encode(body?.password ?: "")) {
            throw new BadRequestException("用户名或密码错误")
        } else {
            def token = tokenService.generateToken(user.id)
            def oldToken = tokenRepository.findByUserId(user.id)
            if (!!oldToken) {
                oldToken.token = token
                oldToken.expiredTime = new Date((System.currentTimeMillis() + appConfig.expiredTime.toBigInteger()).longValue())
                return tokenRepository.save(oldToken)
            }else {
                return tokenRepository.save(new Token(userId: user.id, token: token, expiredTime: new Date((System.currentTimeMillis() + appConfig.expiredTime.toBigInteger()).longValue())))
            }

        }

    }


    @ApiOperation(value = "注册", notes = "根据传入的信息来进行注册,companyId非必填项,注册时可以先不指定, body example: {\"username\": \"jasonzhang\", \"password\": \"leftjs\", \"gender\": \"男\", \"nickname\": \"小张\"}" , response = User.class)
    @RequestMapping(path = "/register", method = RequestMethod.POST)
    def register(@RequestPart String body, @RequestPart MultipartFile[] files) {
        UserRegisterRequestModel newBody = ValidationUtil.validateModelAttribute(UserRegisterRequestModel.class,body)
        def company = !!newBody?.companyId ? companyRepository.findOne(newBody?.companyId) : null
        def currentAvatar = ossService.uploadFileToOSS(files?.first())
        if (userRepository.findByUsername(newBody?.username)&& newBody?.username != null) {
            throw new DatabaseDuplicateException("用户名为${newBody?.username}的用户已经存在")
        } else if (userRepository.findByEmail(newBody?.email)&& newBody?.email != null) {
            throw new DatabaseDuplicateException("邮箱为${newBody?.email}的用户已经存在")
        } else if (userRepository.findByPhone(newBody?.phone)) {
            throw new DatabaseDuplicateException("手机号码为${newBody?.phone}的用户已经存在")
        } else {
            def savedUser =  userRepository.save(new User(
                    nickname: newBody.nickname ?: "匿名",
                    gender: newBody?.gender,
                    avatar: currentAvatar,
                    username: newBody?.username,
                    password: MD5Util.md5Encode(newBody?.password),
                    phone: newBody?.phone,
                    email: newBody?.email,
                    companyId: !!company ? company?.id : null
            ))
            //注册环信用户
            String uuid = easemobController.createUser(savedUser.id,savedUser.password,savedUser.username)
            savedUser.easemobId = uuid
            userRepository.save(savedUser)
        }
    }

    @ApiOperation(value = "获取验证码", notes = "获取验证码")
    @RequestMapping(path = "/code", method = RequestMethod.GET)
    def getCode(@RequestParam String phone) {
        Random random = new Random()
        int code = random.nextInt(9999-1000+1)+1000
        def res = yunpianService.sendSms(code, phone)
        net.sf.json.JSONObject jsonObject1 = net.sf.json.JSONObject.fromObject(res)
        if (jsonObject1.get("code").equals(0)) {
            JSONObject jsonObject = new JSONObject()
            jsonObject.put("code",code)
            return ResponseMsg.ok(jsonObject)
        }
        return ResponseMsg.error("发送验证码错误,请检查",400)
    }

    /**
     * 添加或修改备注
     * @param newMemo
     * @param colleagueId
     * @param req
     * @return
     */
    @ApiOperation(value = "添加备注", notes = "根据传入的colleagueId,对我的同事进行备注,colleagueId为该同事的用户Id")
    @RequestMapping(path = "/profile/add-memo", method = RequestMethod.POST)
    @ApiImplicitParam(value = "x-token", required = true, paramType = "header", name = "x-token")
    def addMemo(@RequestBody UserAddMemoRequestModel body,
                HttpServletRequest req) {
        def newMemo = body.memo
        def colleagueId = body.colleagueId
        def user = req.getAttribute("user") as User
        def colleague = userRepository.findOne(colleagueId)
        String colleagueItemId
        def colleagueItem
        boolean remarked = false
        if (user.addressBook) {
            for (int i = 0; i < user.addressBook.size(); i++) {
                def currentItem = colleagueItemRepository.findOne(user.addressBook[i])
                if (currentItem.colleagueId == colleagueId) {
                    colleagueItemId = currentItem.id
                    remarked = true
                    break
                }
            }
        }
        colleagueItem = !!colleagueItemId ? colleagueItemRepository.findOne(colleagueItemId) : null
        if (!colleagueItem) {
            def newList
            def item = colleagueItemRepository.save(new ColleagueItem(
                    colleagueId: colleagueId,
                    colNickName: colleague.nickname,
                    phoneNum: colleague.phone,
                    avatar: colleague.avatar,
                    memo: newMemo
            ))
            newList = !!user.addressBook ? user.addressBook : []
            newList.add(item.id)
            user.addressBook = newList
            user.addressBook.each {
                def current = colleagueItemRepository.findOne(it)
                if (!current) {
                    user.addressBook.remove(it)
                }
                userRepository.save(user)
            }
            userRepository.save(user)
        } else {
            colleagueItem.memo = newMemo
            colleagueItemRepository.save(colleagueItem)
            user.addressBook.each {
                def current = colleagueItemRepository.findOne(it)
                if (!current) {
                    user.addressBook.remove(it)
                }
                userRepository.save(user)
            }
        }

        return generateResponseSimpleUserModelByPersistentUser(colleague,newMemo)
    }

    /**
     * 获取我的同事
     * @param req
     * @return
     */
    @ApiOperation(value = "我的同事", notes = "获取当前登录用户的同事列表",response = SimpleUserModel.class)
    @RequestMapping(path = "/profile/myColleague", method = RequestMethod.GET)
    @ApiImplicitParam(value = "x-token", required = true, paramType = "header", name = "x-token")
    def getMyColleague(HttpServletRequest req) {
        def user =req.getAttribute("user") as User
        def list = userRepository.findAllByCompanyId(user.companyId)
        def result = []
        !!list ? list.each {
            if (it.id != user.id) {
                def currentRemark = ""
                if (user.addressBook) {
                    for (int i = 0; i<user.addressBook.size(); i++) {
                        def currentCol = colleagueItemRepository.findOne(user.addressBook[i])
                        if (currentCol.colleagueId == it.id) {
                            currentRemark = currentCol.memo
                            break
                        }
                    }
                }
                result.add(generateResponseSimpleUserModelByPersistentUser(it, currentRemark))
            }
        } : null
        return result

    }

    /**
     * 获取三张用户的动态图片
     * @param userId
     * @return
     */
    @ApiOperation(value = "用户的动态图片", notes = "获取三张用户的动态图片", response = ColleagueTrendItem.class)
    @RequestMapping(path = "/image/{userId}", method = RequestMethod.GET)
    def getMyColleagueTrendImage(@PathVariable("userId") String userId) {
        def currentUser = !!userId ? userRepository.findOne(userId) :null
        def list
        def newList = []

        if (currentUser) {
            list = trendItemRepository.findByAuthorId(userId, new PageRequest(0, 3,
                    new Sort(Arrays.asList(new Sort.Order(Sort.Direction.DESC, "createdAt")))))
        } else {
            return ResponseMsg.error("请传入正确的用户Id",200)
        }
        list.each {
            switch (it.typeEnum) {
                case Constants.TypeEnum.Showtime:
                    def newShowtime = showtimeRepository.findOne(it.trendId)
                    (!!newShowtime.images.size()>0) ? newList.add(new ColleagueTrendItem(imgurl: newShowtime.images, createdAt: newShowtime.createdAt)) : null
                    break
                case Constants.TypeEnum.Activity:
                    def newActivity = activityRepository.findOne(it.trendId)
                    !!newActivity.image ? newList.add(new ColleagueTrendItem(imgurl: newActivity.image, createdAt: newActivity.createdAt)) : null
                    break
                case Constants.TypeEnum.Topic:
                    def newTopic = topicRepository.findOne(it.trendId)
                    !!newTopic.image ? newList.add(new ColleagueTrendItem(imgurl: newTopic.image, createdAt: newTopic.createdAt)) : null
                    break
                case Constants.TypeEnum.Vote:
                    def newVote = voteRepository.findOne(it.trendId)
                    !!newVote.image ? newList.add(new ColleagueTrendItem(imgurl: newVote.image, createdAt: newVote.createdAt)) : null
                    break
            }
        }


        return newList
    }

    /**
     * 获取个人资料
     * @param req
     * @return
     */
    @ApiOperation(value = "个人资料", notes = "获取当前登录用户的个人资料" , response = ResUser.class)
    @RequestMapping(path = "/profile", method = RequestMethod.GET)
    @ApiImplicitParam(value = "x-token", required = true, paramType = "header", name = "x-token")
    def profile(HttpServletRequest req) {
        def user = req.getAttribute("user") as User
        return generateResponseUserByPersistentUser(user)

    }

    /**
     * 获取当前用户已发布的投票
     * @param req
     * @return
     */
    @ApiOperation(value = "已经发布的投票", notes = "获取的当前登录用户发布的投票")
    @RequestMapping(path = "/profile/votes", method = RequestMethod.GET)
    @ApiImplicitParam(value = "x-token", required = true, paramType = "header", name = "x-token")
    def getVotes(HttpServletRequest req) {
        def user = req.getAttribute("user") as User
        def result = []
        user?.votes?.each {
            def vote = voteRepository.findOne(it)
            result.add(trendController.generateResponseVoteByPersistentVote(vote,user))
        }
        return result
    }

    /**
     * 获取当前用户已发布的话题
     * @param req
     * @return
     */
    @ApiOperation(value = "已经发布的话题", notes = "获取的当前登录用户发布的话题")
    @RequestMapping(path = "/profile/topics", method = RequestMethod.GET)
    @ApiImplicitParam(value = "x-token", required = true, paramType = "header", name = "x-token")
    def getTopics(HttpServletRequest req) {
        def user = req.getAttribute("user") as User
        def result = []
        user?.topics?.each {
            def topic = topicRepository.findOne(it)
            result.add(trendController.generateResponseTopicByPersistentTopic(topic))
        }
        return result
    }

    /**
     * 获取当前用户已发布的活动
     * @param req
     * @return
     */
    @ApiOperation(value = "已经发布的活动", notes = "获取的当前登录用户发布的活动")
    @RequestMapping(path = "/profile/activities", method = RequestMethod.GET)
    @ApiImplicitParam(value = "x-token", required = true, paramType = "header", name = "x-token")
    def getActivities(HttpServletRequest req) {
        def user = req.getAttribute("user") as User
        def result = []
        user?.activities?.each {
            def activity = activityRepository.findOne(it)
            result.add(trendController.generateResponseActivityByPersistentActivity(activity,user))
        }
        return result
    }

    /**
     * 获取个人的时间轴
     * @param trendId
     * @param page
     * @param limit
     * @param req
     * @return
     */
    @ApiOperation(value = "个人页面", notes = "获取当前登录用户的时间轴,返回值有为四种动态类型")
    @RequestMapping(path = "/personal/timeline", method = RequestMethod.GET)
    def getPersonalTimeline(
            @RequestParam(required = false)
            @ApiParam("页数,默认第0页")
                    String userId,
            @RequestParam(required = false)
            @ApiParam("页数,默认第0页")
                    String trendId,
            @RequestParam(required = false)
            @ApiParam("页数,默认第0页")
                    Integer page,
            @RequestParam(required = false)
            @ApiParam("每页条数,默认10条")
                    Integer limit) {
        def user =!!userId ? userRepository.findOne(userId) : null
        if (user) {
            def list
            def currentTrend = !!trendId ? trendItemRepository.findByTrendId(trendId) : null
            def newList = []
            if (!currentTrend) {
                //为空则返回最新的动态
                list = trendItemRepository.findByAuthorId(user.id, new PageRequest(
                        page ?: 0,
                        limit ?: 10,
                        new Sort(Arrays.asList(new Sort.Order(Sort.Direction.DESC, "createdAt")))))
            } else {
                list = trendItemRepository.findByAuthorIdAndCreatedAt(user.id, currentTrend.createdAt, new PageRequest(
                        page ?: 0,
                        limit ?: 10,
                        new Sort(Arrays.asList(new Sort.Order(Sort.Direction.DESC, "createdAt")))))
            }

            list.each {
                switch (it.typeEnum) {
                    case Constants.TypeEnum.Showtime:
                        def newShowtime = showtimeRepository.findOne(it.trendId)
                        newList.add(trendController.generateResponseShowtimeByPersistentShowtime(newShowtime, user))
                        break
                    case Constants.TypeEnum.Activity:
                        def newActivity = activityRepository.findOne(it.trendId)
                        newList.add(trendController.generateResponseActivityByPersistentActivity(newActivity, user))
                        break
                    case Constants.TypeEnum.Topic:
                        def newTopic = topicRepository.findOne(it.trendId)
                        newList.add(trendController.generateResponseTopicByPersistentTopic(newTopic))
                        break
                    case Constants.TypeEnum.Vote:
                        def newVote = voteRepository.findOne(it.trendId)
                        newList.add(trendController.generateResponseVoteByPersistentVote(newVote, user))
                        break
                }
            }
            def dic = [:]
            dic["votesNumber"] = user?.votes?.size()?:0
            dic["topicsNumber"] = user?.topics?.size()?:0
            dic["activitiesNumber"] = user?.activities?.size()?:0
            dic["content"] = newList
            dic["last"] = list.last
            dic["first"] = list.first
            dic["totalElement"] = list.totalElements
            dic["totalPages"] = list.totalPages
            dic["size"] = list.size
            dic["number"] = list.number
            dic["numberOfElements"] = list.numberOfElements
            def sort
            sort = list.sort
            dic["sort"] = sort
            return dic
        } else {
            return ResponseMsg.error("请传入正确的userId",200)
        }


    }


    /**
     * 选择加入群组
     * @param userId
     * @param teamId
     * @return
     */
    @ApiOperation(value = "加入群组", notes = "根据传入的群组id,选择加入群组", response = SimpleTeamModel.class)
    @ApiImplicitParam(value = "x-token", required = true, paramType = "header", name = "x-token")
    @RequestMapping(path = "/{userId}/choose/team/{teamId}", method = RequestMethod.GET)
    def chooseTeam(@PathVariable String userId, @PathVariable String teamId) {
        def currentUser = !!userId ? userRepository.findOne(userId) : null
        def currentTeam = !!teamId ? teamRepository.findOne(teamId) : null
        if (!currentUser) {
            throw new NotFoundException("id为 ${userId} 的用户不存在")
        }
        if (!currentTeam) {
            throw new NotFoundException("id为 ${teamId} 的群组不存在")
        }
//        for(String member : team.members){
//            if (user.id == member){
//                return ResponseMsg.ok("你已加入该群组",200,generateResponseMyGroupByPersistentUser(user))
//            }
//        }
//        team.members.add(user.id)
//        teamRepository.save(team)
//
//        user.myGroup.add(team.id)
//        user.myGroup.unique()
//        userRepository.save(user)
//        return ResponseMsg.ok(generateResponseMyGroupByPersistentUser(user))

        def members = []
        members.add(currentUser?.id)
        ResponseMsg message = easemobController.inviteChatGroupMembers(currentTeam.easemobId, new TeamInviteMembersRequestBody(membersId: members,teamId: currentTeam?.id)) as ResponseMsg
        if (!message.statusCode.equals(200)) {
            return ResponseMsg.error("邀请失败", 400)
        }
        currentTeam.members.add(currentUser?.id)
        currentTeam.members.unique()
        currentUser.myGroup.add(currentTeam?.id)
        currentUser.myGroup.unique()
        userRepository.save(currentUser)
        teamRepository.save(currentTeam)
        return ResponseMsg.ok("你已加入该群组",200,generateResponseMyGroupByPersistentUser(currentUser))

    }

    /**
     * 获取当前登录用户的群组列表
     * @param req
     * @return
     */
    @ApiOperation(value = "获取当前用户的群组", notes = "根据当前登录的用户,获取群组列表", response = SimpleTeamModel.class)
    @RequestMapping(path = "/profile/get-myGroup", method = RequestMethod.GET)
    @ApiImplicitParam(value = "x-token", required = true, paramType = "header", name = "x-token")
    def getMyGroup(@RequestParam(required = false)
                       @ApiParam("")
                               String teamId,
                   @RequestParam(required = false)
                       @ApiParam("页数,默认第0页")
                               Integer page,
                   @RequestParam(required = false)
                       @ApiParam("每页条数,默认10条")
                               Integer limit,
                    HttpServletRequest req) {
        def user = req.getAttribute("user") as User
        def list
        def perteamId = !!teamId ? teamRepository.findOne(teamId) : null
        if (!perteamId) {
            list = teamRepository.findByMembersContaining(user.id, new PageRequest(
                    page ?: 0,
                    limit ?: 10,
                    new Sort(Arrays.asList(new Sort.Order(Sort.Direction.DESC, "createdAt")))))
        } else {
            list = teamRepository.findByMembersContainingAndCreatedAtBefore(user.id,perteamId.createdAt, new PageRequest(
                    page ?: 0,
                    limit ?: 10,
                    new Sort(Arrays.asList(new Sort.Order(Sort.Direction.DESC, "createdAt")))))
        }



        return list.map(new Converter() {
            @Override
            Object convert(Object source) {
                return generateResponseMyGroupByPersistentUser(source as com.donler.model.persistent.team.Team, user)
            }
        })

    }

    /**
     * 选择公司
     * @param userId
     * @param companyId
     * @return
     */
    @ApiOperation(value = "选择公司", notes = "用户选择公司", response = ResponseMsg.class)
    @RequestMapping(path = "/choose/company/{companyId}", method = RequestMethod.GET)
    @ApiImplicitParam(value = "x-token", required = true, paramType = "header", name = "x-token")
    def chooseCompany(@PathVariable(value = "companyId") String companyId,HttpServletRequest req) {
        def user = req.getAttribute("user") as User
        def company = !!companyId ? companyRepository.findOne(companyId) : null
//        def user = !!user?.id ? userRepository.findOne(userId) : null
        if (!user) {
            throw new NotFoundException("id为 ${user?.id} 的用户不存在")
        }
        if (!company) {
            throw new NotFoundException("id为 ${companyId} 的公司不存在")
        }
        user.companyId = company?.id
        return ResponseMsg.ok(generateResponseUserByPersistentUser(userRepository.save(user)))
    }


    @ApiOperation(value = "选择头像上传", notes = "用户更换头像", response = ResponseMsg.class)
    @RequestMapping(path = "/{userId}/avatar", method = RequestMethod.POST)
    def chooseAvatar(@RequestPart MultipartFile[] files, @PathVariable(value = "userId") String userId) {
        def currentAvatar = ossService.uploadFileToOSS(files?.first())
        def user = !!userId ? userRepository.findOne(userId) : null
        if (!user) {
            throw new NotFoundException("id为 ${userId} 的用户不存在")
        }
        user.avatar = currentAvatar
        return ResponseMsg.ok(generateResponseUserByPersistentUser(userRepository.save(user)))


    }

    /**
     * 个人信息更新(昵称/真名/星座/生日/职位)
     * @param body
     * @param req
     * @return
     */
    @ApiOperation(value = "个人信息更新", notes = "更新(昵称/真名/星座/生日/职位)")
    @RequestMapping(path = "/profile/update", method = RequestMethod.PUT)
    @ApiImplicitParam(value = "x-token", required = true, paramType = "header", name = "x-token")
    def modProfile(@Valid @RequestBody UserProfileModifyRequestModel body, HttpServletRequest req) {
        def user = req.getAttribute("user") as User
        !!body.nickname ? user.nickname = body.nickname : null
        !!body.realname ? user.realname = body.realname : null
        !!body.constellation ? user.constellation = body.constellation : null
        !!body.birthday ? user.birthday = body.birthday : null
        !!body.job ? user.job = body.job : null
        userRepository.save(user)
        return generateResponseUserByPersistentUser(user)
    }

    /**
     * 根据持久化User生成响应的User
     * @param user
     * @return
     */
    def generateResponseUserByPersistentUser(User user) {
        def company = !!user?.companyId ? companyRepository.findOne(user?.companyId) : null
        return new ResUser(
                id: user?.id,
                nickname: user?.nickname,
                realname: user?.realname,
                constellation: user?.constellation,
                birthday: user?.birthday,
                gender: user?.gender,
                avatar: user?.avatar,
                job: user?.job,
                username: user?.username,
                phone: user?.phone,
                email: user?.email,
                company: !!company ? new SimpleCompanyModel(
                        id: company?.id,
                        name: company?.name,
                        imageUrl: company?.image
                ) : null,
                topicsNum: !!user?.topics?.size() ?  user?.topics?.size() : 0,
                votesNum: !!user?.votes?.size() ? user?.votes?.size() : 0,
                activitiesNum: !!user?.activities?.size() ? user?.activities?.size() : 0,
                easemobid: user?.easemobId


        )
    }

    /**
     * 根据持久化User的addressBook生成响应的AddressBook
     * @param user
     * @return
     */
    def generateResponseAddressBookByPersistentUser(User user) {
         def addBook
         addBook = user?.addressBook?.collect {
             def  item = colleagueItemRepository.findOne(it)
             return item

                }
        return addBook as JSON

    }

    /**
     * 根据持久化User生成响应的我的群组
     * @param user
     * @return
     */
    def generateResponseMyGroupByPersistentUser(User user){
        def myGroup
        myGroup = user?.myGroup?.collect {
            def item = teamRepository.findOne(it)
            return new SimpleTeamModel(
                    id: item?.id,
                    name: item?.name,
                    imageUrl: item?.image,
                    easemobId: item?.easemobId,
                    isJoined: !!teamRepository.findOne(item.id).members ? teamRepository.findOne(item.id).members.contains(user.id) : false
            )
        }
        return myGroup as JSON
    }

    def generateResponseMyGroupByPersistentUser(com.donler.model.persistent.team.Team team,User user){
            return new SimpleTeamModel(
                    id: team?.id,
                    name: team?.name,
                    imageUrl: team?.image,
                    easemobId: team?.easemobId,
                    isJoined: !!teamRepository.findOne(team.id).members ? teamRepository.findOne(team.id).members.contains(user.id) : false
            )

    }

    /**
     * 根据SimpleUserModel生成响应的我的同事
     * @param user
     * @param remark
     * @return
     */
    static def generateResponseSimpleUserModelByPersistentUser(User user, String remark) {
        def result = new SimpleUserModel(
                id: user.id,
                nickname: user.nickname,
                avatar: user.avatar,
                realname: user.realname,
                phone: user.phone,
                remark: remark,
                job: user.job

        )
        return result
    }


}
