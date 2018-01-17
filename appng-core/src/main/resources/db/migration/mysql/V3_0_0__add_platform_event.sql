create table platform_event (
	id integer not null auto_increment,
	created datetime(3),
	ev_type varchar(255),
	ev_user varchar(255),
	event varchar(255),
	contextPath varchar(255),
	servletPath varchar(255),
	host varchar(255),
	hostName varchar(255),
	primary key (id)
);